# 已读追踪功能使用指南

## 功能概述

已读追踪功能是WeKit的一个增强模块，允许用户监控消息的已读状态和访客信息。该功能通过内嵌HTTP服务器和公网隧道实现，无需额外的服务器配置。

## 主要特性

- **消息注册**: 为每条消息生成唯一的追踪ID
- **已读监控**: 实时监控消息的已读状态
- **访客追踪**: 记录访问者的IP地址、地理位置、设备信息等
- **公网隧道**: 自动配置cloudflared隧道，提供公网访问地址
- **控制台界面**: 提供Web控制台查看统计信息和详细数据
- **数据存储**: 使用SQLite数据库存储所有追踪数据

## 使用方法

### 1. 启用已读追踪功能

1. 在WeKit主界面中，找到"已读追踪"功能入口
2. 点击进入已读追踪管理界面
3. 点击"启动服务"按钮启动已读追踪服务

### 2. 查看服务状态

启动后，界面会显示：
- 服务运行状态
- 公网隧道地址（如果已启动）
- 统计信息（已注册消息数、独立访客数）

### 3. 使用追踪功能

#### 注册消息
通过API注册需要追踪的消息：
```http
POST /register
Content-Type: application/json

{
  "wxId": "your_wechat_id",
  "content": "消息内容",
  "createTime": 1234567890000
}
```

响应：
```json
{
  "success": true,
  "id": "sha256_hash",
  "wxId": "your_wechat_id",
  "pixel_url": "http://127.0.0.1:5000/pixel?wxId=your_wechat_id&id=sha256_hash"
}
```

#### 获取已读统计
```http
GET /count?wxId=your_wechat_id&id=message_id
```

响应：
```json
{
  "count": 5,
  "msg_id": "message_id"
}
```

### 4. 访问控制台

1. 在已读追踪界面中点击"控制台"
2. 或直接访问公网隧道地址（如：`https://xxx.trycloudflare.com`）
3. 在Web控制台中可以查看：
   - 所有已注册消息列表
   - 每条消息的已读统计
   - 详细访客信息（IP、地理位置、设备等）
   - 删除消息或清空数据

### 5. 查看运行日志

在已读追踪界面中点击"运行日志"，可以查看：
- cloudflared隧道启动日志
- HTTP服务器运行日志
- 错误和异常信息

## API文档

### 端点

#### 1. 健康检查
```
GET /health
```

#### 2. 注册消息
```
POST /register
Content-Type: application/json

{
  "wxId": "string",
  "content": "string",
  "createTime": "timestamp (optional)"
}
```

#### 3. 获取已读计数
```
GET /count?wxId=string&id=string
```

#### 4. 获取消息列表
```
GET /api/messages
```

#### 5. 获取消息详情和已读记录
```
GET /api/reads/{message_id}
```

#### 6. 删除消息
```
POST /api/delete/{message_id}
```

#### 7. 清空所有数据
```
POST /api/delete-all
```

#### 8. 批量状态查询
```
GET /batch-status?ids=id1,id2,id3
```

#### 9. 追踪像素
```
GET /pixel?wxId=string&id=string&reader=string (optional)
```

### 响应格式

所有API响应都使用JSON格式，包含以下字段：
- `success`: 操作是否成功
- `error`: 错误信息（如果有）
- `data`: 响应数据（如果有）

## 技术架构

### 组件说明

1. **ReadReceiptService**: 核心服务，负责：
   - HTTP服务器管理
   - cloudflared隧道启动和管理
   - 服务生命周期控制

2. **ReadReceiptDatabase**: 数据库管理，负责：
   - 消息和已读记录的存储
   - 数据查询和统计
   - 数据清理

3. **ConsoleHtml**: Web控制台生成，负责：
   - 生成HTML页面
   - 提供用户界面
   - 处理用户交互

4. **GeoLookup**: 地理位置查询，负责：
   - IP地址解析
   - 地理位置信息获取

### 数据库结构

#### messages表
- `id`: 消息唯一标识（SHA256哈希）
- `wx_id`: 微信ID
- `content`: 消息内容
- `create_time`: 创建时间戳
- `registered_at`: 注册时间戳

#### reads表
- `id`: 自增主键
- `msg_id`: 关联的消息ID
- `wx_id`: 微信ID
- `ip_address`: 访问者IP地址
- `user_agent`: 用户代理字符串
- `country`: 国家
- `region`: 地区
- `city`: 城市
- `isp`: 运营商
- `loc`: 经纬度坐标
- `reader_wx_id`: 阅读者微信ID
- `read_at`: 阅读时间戳

## 安全考虑

1. **数据隐私**: 所有数据都存储在本地设备上
2. **网络安全**: 使用HTTPS隧道加密传输
3. **访问控制**: 服务仅在本地运行，不暴露到公网
4. **数据清理**: 提供数据删除功能

## 故障排除

### 常见问题

1. **服务无法启动**
   - 检查网络连接
   - 确认cloudflared二进制文件存在
   - 查看日志文件获取详细错误信息

2. **隧道无法连接**
   - 检查防火墙设置
   - 确认网络环境允许HTTPS连接
   - 重启服务

3. **数据无法保存**
   - 检查存储权限
   - 确认数据库文件未损坏
   - 重启应用

### 日志查看

日志文件位置：`/data/data/包名/files/cloudflared.log`

可以通过以下方式查看：
1. 在已读追踪界面点击"运行日志"
2. 使用文件管理器直接访问
3. 通过adb命令查看

## 性能优化

1. **数据库优化**: 定期清理旧数据
2. **内存管理**: 合理使用线程池
3. **网络优化**: 使用连接池管理HTTP连接
4. **缓存机制**: 缓存频繁查询的数据

## 更新和维护

### 版本更新

1. 备份现有数据
2. 更新应用版本
3. 恢复数据（如果需要）

### 数据迁移

如需迁移数据：
1. 导出SQLite数据库
2. 在新设备上导入
3. 验证数据完整性

## 贡献指南

欢迎提交问题和改进建议！

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 发起Pull Request

## 许可证

本项目遵循WeKit的开源许可证。

## 联系方式

- GitHub: https://github.com/Ujhhgtg/WeKit
- Telegram: https://t.me/+7j5dJ6g16B43OWVl