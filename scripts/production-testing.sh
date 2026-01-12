#!/usr/bin/env sh
# POSIX sh (no bashisms)
set -eu

# ------------------------------------------------------------------------------
# Production/LocalStack testing run:
# 1) (Optional) Drain transactions + decision queues (DANGEROUS on real AWS).
# 2) Send a batch of messages to transaction queue.
# 3) Wait/poll until decisions appear (timeout).
# 4) Print decision queue approximate counts.
#
# Safe defaults:
# - Does NOT drain queues unless you explicitly enable it.
# ------------------------------------------------------------------------------

TOTAL_MESSAGES="${TOTAL_MESSAGES:-10}"

# Old fixed sleep is kept, but you now also have wait/poll controls:
SLEEP_SECS="${SLEEP_SECS:-0}"                 # optional initial sleep before polling
WAIT_TIMEOUT_SECS="${WAIT_TIMEOUT_SECS:-120}" # max time to wait for decisions
WAIT_POLL_SECS="${WAIT_POLL_SECS:-5}"         # poll interval

# Expect 1 decision per transaction by default; override if your system differs
EXPECT_DECISIONS="${EXPECT_DECISIONS:-$TOTAL_MESSAGES}"

TX_ID_PREFIX="${TX_ID_PREFIX:-prod-test-$(date +%s)-}"

QUEUE_NAME="${QUEUE_NAME:-fintech-transactions}"
DECISIONS_QUEUE_NAME="${DECISIONS_QUEUE_NAME:-fintech-decisions}"

QUEUE_URL="${QUEUE_URL:-}"
DECISIONS_QUEUE_URL="${DECISIONS_QUEUE_URL:-}"

REGION="${REGION:-${AWS_REGION:-us-east-1}}"
AWS_PROFILE="${AWS_PROFILE:-}"

CONFIGMAP_FILE="${CONFIGMAP_FILE:-deploy/k8s/base/configmap.yaml}"
CONFIGMAP_TX_KEY="${CONFIGMAP_TX_KEY:-SQS_QUEUE_URL}"
CONFIGMAP_DECISIONS_KEY="${CONFIGMAP_DECISIONS_KEY:-OUTBOX_DECISION_QUEUE_URL}"

USE_LOCALSTACK="${USE_LOCALSTACK:-0}"
LOCALSTACK_COMPOSE_FILE="${LOCALSTACK_COMPOSE_FILE:-docker-compose.localstack.yml}"
LOCALSTACK_SERVICE_NAME="${LOCALSTACK_SERVICE_NAME:-localstack}"

DRAIN_TX="${DRAIN_TX:-0}"
DRAIN_DECISIONS="${DRAIN_DECISIONS:-0}"
CONFIRM_DRAIN="${CONFIRM_DRAIN:-}"

DRAIN_MAX_MESSAGES="${DRAIN_MAX_MESSAGES:-1000}"

GRADLEW="${GRADLEW:-./gradlew}"
ENCODE_TASK="${ENCODE_TASK:-encodeSqsMessage}"

log() { printf '%s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

AWS_MODE="aws"
if [ "$USE_LOCALSTACK" = "1" ]; then
  if command -v awslocal >/dev/null 2>&1; then
    AWS_MODE="awslocal"
  elif command -v docker >/dev/null 2>&1; then
    AWS_MODE="docker_awslocal"
  else
    die "USE_LOCALSTACK=1 but neither awslocal nor docker is available"
  fi
else
  command -v aws >/dev/null 2>&1 || die "aws CLI not found"
  AWS_MODE="aws"
fi

aws_cli() {
  case "$AWS_MODE" in
    aws)
      if [ -n "${AWS_PROFILE:-}" ]; then
        aws --region "$REGION" --profile "$AWS_PROFILE" "$@"
      else
        aws --region "$REGION" "$@"
      fi
      ;;
    awslocal)
      awslocal --region "$REGION" "$@"
      ;;
    docker_awslocal)
      docker compose -f "$LOCALSTACK_COMPOSE_FILE" exec -T "$LOCALSTACK_SERVICE_NAME" awslocal --region "$REGION" "$@"
      ;;
    *)
      die "Unknown AWS_MODE=$AWS_MODE"
      ;;
  esac
}

resolve_queue_url() {
  aws_cli sqs get-queue-url \
    --queue-name "$1" \
    --query 'QueueUrl' \
    --output text
}

configmap_value() {
  key="$1"
  [ -f "$CONFIGMAP_FILE" ] || return 0

  awk -v key="$key" '
    {
      line=$0
      sub(/^[[:space:]]+/, "", line)
      if (line ~ ("^" key ":")) {
        sub("^" key ":[[:space:]]*", "", line)
        gsub(/^[[:space:]]+/, "", line)
        gsub(/^"+|"+$/, "", line)
        print line
        exit
      }
    }
  ' "$CONFIGMAP_FILE"
}

require_drain_confirmation_if_real_aws() {
  [ "$USE_LOCALSTACK" = "1" ] && return 0
  if [ "$CONFIRM_DRAIN" != "YES" ]; then
    die "Refusing to drain real AWS queues unless CONFIRM_DRAIN=YES (you set DRAIN_TX/DRAIN_DECISIONS=1)"
  fi
}

drain_queue() {
  queue_url="$1"
  label="$2"

  require_drain_confirmation_if_real_aws

  total=0
  while :; do
    receipts="$(aws_cli sqs receive-message \
      --queue-url "$queue_url" \
      --max-number-of-messages 10 \
      --wait-time-seconds 1 \
      --query 'Messages[].ReceiptHandle' \
      --output text || true)"

    if [ -z "${receipts:-}" ] || [ "${receipts:-}" = "None" ]; then
      break
    fi

    for receipt in $receipts; do
      aws_cli sqs delete-message \
        --queue-url "$queue_url" \
        --receipt-handle "$receipt" >/dev/null
      total=$((total + 1))
      if [ "$total" -ge "$DRAIN_MAX_MESSAGES" ]; then
        log "Reached DRAIN_MAX_MESSAGES=$DRAIN_MAX_MESSAGES for $label; stopping drain."
        printf '%s\n' "$total"
        return 0
      fi
    done
  done

  printf '%s\n' "$total"
}

get_queue_attr() {
  # $1 = queue_url, $2 = attribute name
  aws_cli sqs get-queue-attributes \
    --queue-url "$1" \
    --attribute-names "$2" \
    --query "Attributes.$2" \
    --output text
}

queue_counts() {
  queue_url="$1"
  visible="$(get_queue_attr "$queue_url" ApproximateNumberOfMessages)"
  inflight="$(get_queue_attr "$queue_url" ApproximateNumberOfMessagesNotVisible)"
  delayed="$(get_queue_attr "$queue_url" ApproximateNumberOfMessagesDelayed)"
  printf '%s\n' "$visible" "$inflight" "$delayed"
}

wait_for_decisions() {
  target="$1"

  start_epoch="$(date +%s)"
  deadline=$((start_epoch + WAIT_TIMEOUT_SECS))

  # Optional initial sleep
  if [ "${SLEEP_SECS:-0}" -gt 0 ]; then
    log "Initial sleep $SLEEP_SECS second(s) before polling..."
    sleep "$SLEEP_SECS"
  fi

  while :; do
    now="$(date +%s)"
    if [ "$now" -ge "$deadline" ]; then
      return 1
    fi

    set -- $(queue_counts "$DECISIONS_QUEUE_URL")
    visible="$1"
    inflight="$2"
    delayed="$3"

    elapsed=$((now - start_epoch))
    log "Wait ${elapsed}s: decisions visible=$visible inflight=$inflight delayed=$delayed (target >= $target)"

    # If your pipeline publishes and nothing consumes decisions, visible should grow.
    # If something consumes decisions quickly, you may need a different success condition.
    if [ "$visible" -ge "$target" ]; then
      return 0
    fi

    sleep "$WAIT_POLL_SECS"
  done
}

# Resolve queue URLs
if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL="$(configmap_value "$CONFIGMAP_TX_KEY" || true)"
fi
if [ -z "$DECISIONS_QUEUE_URL" ]; then
  DECISIONS_QUEUE_URL="$(configmap_value "$CONFIGMAP_DECISIONS_KEY" || true)"
fi
if [ -z "$QUEUE_URL" ]; then
  QUEUE_URL="$(resolve_queue_url "$QUEUE_NAME" || true)"
fi
if [ -z "$DECISIONS_QUEUE_URL" ]; then
  DECISIONS_QUEUE_URL="$(resolve_queue_url "$DECISIONS_QUEUE_NAME" || true)"
fi

[ -n "$QUEUE_URL" ] && [ "$QUEUE_URL" != "None" ] || die "Missing QUEUE_URL"
[ -n "$DECISIONS_QUEUE_URL" ] && [ "$DECISIONS_QUEUE_URL" != "None" ] || die "Missing DECISIONS_QUEUE_URL"

log "Using mode: $AWS_MODE (region=$REGION)"
log "TX_ID_PREFIX=$TX_ID_PREFIX"
log "Queues:"
log "  transactions: $QUEUE_URL"
log "  decisions:    $DECISIONS_QUEUE_URL"

# Optional draining (guarded)
if [ "$DRAIN_TX" = "1" ]; then
  log "Draining transaction queue ($QUEUE_NAME)..."
  drained_tx="$(drain_queue "$QUEUE_URL" "$QUEUE_NAME")"
  log "Drained $drained_tx message(s) from $QUEUE_NAME."
fi

if [ "$DRAIN_DECISIONS" = "1" ]; then
  log "Draining decisions queue ($DECISIONS_QUEUE_NAME)..."
  drained_dec="$(drain_queue "$DECISIONS_QUEUE_URL" "$DECISIONS_QUEUE_NAME")"
  log "Drained $drained_dec message(s) from $DECISIONS_QUEUE_NAME."
fi

# Send messages
[ -x "$GRADLEW" ] || die "Gradle wrapper not found/executable at $GRADLEW"

log "Sending $TOTAL_MESSAGES message(s) to $QUEUE_NAME ..."
i=1
while [ "$i" -le "$TOTAL_MESSAGES" ]; do
  tx_id="${TX_ID_PREFIX}${i}"
  payload="$(TX_ID="$tx_id" "$GRADLEW" -q "$ENCODE_TASK")"
  [ -n "$payload" ] || die "encode task produced empty payload for TX_ID=$tx_id"

  aws_cli sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$payload" >/dev/null

  i=$((i + 1))
done

# Wait until decisions appear (or timeout)
log "Waiting for decisions: expect >= $EXPECT_DECISIONS (timeout=${WAIT_TIMEOUT_SECS}s, poll=${WAIT_POLL_SECS}s)"
if wait_for_decisions "$EXPECT_DECISIONS"; then
  log "Success: decisions visible >= $EXPECT_DECISIONS"
else
  log "Timed out waiting for decisions (expect >= $EXPECT_DECISIONS)."
fi

# Final counts
set -- $(queue_counts "$DECISIONS_QUEUE_URL")
visible="$1"
inflight="$2"
delayed="$3"

log "Decision queue counts (Approximate):"
log "  visible:   $visible"
log "  in-flight: $inflight"
log "  delayed:   $delayed"

# Fail CI/smoke test if not reached expected decisions
if [ "$visible" -lt "$EXPECT_DECISIONS" ]; then
  exit 2
fi

exit 0
