# Dockerfile — 多环境构建 (Python 版本)
FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --create-home appuser

WORKDIR /app

# uv 安装
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# 复制项目配置
COPY pyproject.toml .
COPY python/ python/

# 安装依赖
RUN uv pip install --system flask && mkdir -p /app/data && chown -R appuser:appuser /app

ENV DATABASE_PATH=/app/data/receipts.db

EXPOSE 5000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD curl -f http://localhost:5000/health || exit 1

USER appuser
CMD ["python", "run.py", "--host", "0.0.0.0", "--port", "5000"]
