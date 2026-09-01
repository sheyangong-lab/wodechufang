# 第三方组件与模型声明

本项目（AGPL-3.0）内置以下第三方资产，版权归各自作者所有，按其原始协议分发：

| 资产 | 用途 | 协议 | 来源 |
|------|------|------|------|
| `client/src/static/models/u2netp.onnx` | 本地抠图（显著性分割，推理全程离线） | Apache-2.0 | [U-2-Net](https://github.com/xuebinqin/U-2-Net) © Xuebin Qin 等 |
| `client/src/static/ort/ort-wasm-simd.wasm` | ONNX 推理运行时（WASM 后端） | MIT | [onnxruntime](https://github.com/microsoft/onnxruntime) © Microsoft |

模型权重按 Apache-2.0 允许再分发；本声明即满足其署名要求。其余依赖见 `client/package.json` / `server/pom.xml`，均为 MIT/Apache-2.0/BSD 系宽松协议。
