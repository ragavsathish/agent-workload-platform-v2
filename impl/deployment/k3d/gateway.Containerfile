FROM python:3.13.7-slim-bookworm@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY impl/deployment/gateway.py /app/gateway.py
COPY impl/deployment/deployment.py /app/deployment.py
USER 65532:65532
EXPOSE 9444
ENTRYPOINT ["python", "/app/gateway.py"]
