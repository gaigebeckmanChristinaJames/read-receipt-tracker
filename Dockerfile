# Dockerfile
# ─────────────────────────────────────
# 构建:  docker build -t read-receipt-tracker .
# 运行:  docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
# ─────────────────────────────────────

FROM python:3.11-slim

# 系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 先安装依赖（利用 Docker 缓存层）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 复制源码
COPY . .

# 数据库持久化目录
RUN mkdir -p /app/data
ENV DATABASE_PATH=/app/data/receipts.db

EXPOSE 5000

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

CMD ["python", "run.py", "--host", "0.0.0.0", "--port", "5000"]
