# Kubernetes

A small kustomize base: Namespace (Pod Security `restricted`), PVC, Deployment,
Service, and an optional Ingress.

```sh
# 1. Create the namespace and the token secret
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl -n howlongtocook create secret generic howlongtocook-token \
  --from-literal=token="$(openssl rand -hex 32)"

# 2. Point the image at your registry (until releases are published, build and push it yourself)
docker build -t registry.example.com/howlongtocook-server:0.1.0 server/
docker push registry.example.com/howlongtocook-server:0.1.0
#    then edit `images:` in kustomization.yaml, or:
#    (cd deploy/kubernetes && kustomize edit set image howlongtocook-server=registry.example.com/howlongtocook-server:0.1.0)

# 3. Deploy
kubectl apply -k deploy/kubernetes
kubectl -n howlongtocook port-forward svc/howlongtocook 8080:80
# open http://localhost:8080/admin
```

Notes:

- **One replica, `Recreate` strategy.** The server uses SQLite, which has a single
  writer, so two pods must never share the volume.
- The pod runs as non-root with a read-only root filesystem; the only writable
  path is the `/data` PVC.
- `HLTC_SECURE_COOKIES=true` is set because the ingress terminates TLS. When you
  only use `port-forward` over plain HTTP, set it to `"false"` or the admin login
  cookie will not stick.
- To expose it, edit the host in `ingress.yaml` and uncomment it in
  `kustomization.yaml`.
