# 📡 API 文档

## 基础信息

- **Base URL**: `http://your-server:5000`
- **Content-Type**: `application/json`

---

## 公开 API

### 1. 健康检查

```http
GET /health
```

**响应**: `{"status":"ok","service":"read-receipt-tracker"}`

### 2. 注册消息

```http
POST /register
Content-Type: application/json

{
    "wxID": "user123",
    "content": "你好，这是一条测试消息",
    "createTime": 1700000000000
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| wxID | string | ✅ | 发送者标识 |
| content | string | | 消息内容 (最大 50000 字符) |
| createTime | int | | 创建时间 (毫秒时间戳)，默认当前时间 |

**响应**:

```json
{
    "success": true,
    "id": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a",
    "wxID": "user123",
    "pixel_url": "http://your-server:5000/pixel?wxID=user123&id=a1b2c3d4..."
}
```

### 3. 追踪像素

```http
GET /pixel?wxID=user123&id=a1b2c3d4...
```

返回 1×1 透明 GIF (`Content-Type: image/gif`)，同时记录已读信息：

- **IP 地址** (支持 X-Forwarded-For)
- **User-Agent**
- **时间戳**
- **地理位置** (如果启用了 GeoIP)

### 4. 查询已读数

```http
GET /count?wxID=user123&id=a1b2c3d4...
```

**响应**: `{"count":5,"msg_id":"a1b2c3d4..."}`

---

## 管理 API (需 API Key)

设置环境变量 `API_KEY` 后，以下接口需要认证：

- Header: `X-API-Key: your-key`
- 或 Query: `?api_key=your-key`

### 5. 批量查询已读状态

```http
GET /batch-status?ids=id1,id2,id3
```

**响应**:

```json
{
    "statuses": {
        "id1": 3,
        "id2": 0,
        "id3": 7
    }
}
```

### 6. 删除单条消息

```http
POST /api/delete/msg_id
```

**响应**: `{"success":true}`

### 7. 清空全部数据

```http
POST /api/delete-all
```

⚠️ 不可恢复！

**响应**: `{"success":true}`

---

## 管理后台页面

| 页面 | 路由 | 说明 |
|------|------|------|
| 仪表盘 | `GET /` | 统计、消息列表、搜索、CSV 导出 |
| 消息详情 | `GET /message/<id>` | 消息内容 + 已读记录 + 地理位置 |
