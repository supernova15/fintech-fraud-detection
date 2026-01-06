# Kubernetes deployment

Base manifests live in `deploy/k8s/base`. Use the overlays for AWS and AWS China:

```sh
kubectl apply -k deploy/k8s/overlays/aws
kubectl apply -k deploy/k8s/overlays/aws-cn
```

Before applying, update the image registry and tag in:

- `deploy/k8s/overlays/aws/kustomization.yaml`
- `deploy/k8s/overlays/aws-cn/kustomization.yaml`

You can also adjust rule thresholds and thread pool settings in:

- `deploy/k8s/base/configmap.yaml`
