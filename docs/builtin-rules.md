# 内置规则说明

内置规则文件：`app/src/main/assets/rules/builtin.json`，格式见 `design.md` 第 6 节。
JSON 不支持注释，所以每条规则的依据记在这里。新增或修改规则时同步更新本文件。

| 规则 id | App | 依据 | 说明 |
| --- | --- | --- | --- |
| `qqlive-splash-skip` | 腾讯视频 9.04 | Mate40（鸿蒙 4.2）快照，2026-11-06 | 右上角"跳过"，旁边有"互动广告"字样。直接点控件无效，模拟手势（按住 100ms）有效，所以 `clickBy: gesture`。开屏广告所在页面 `SplashHomeActivity` 同时也是首页，且打开初期页面名可能尚未识别，故 `activities` 用 `*`，以"广告"字样为前提条件 |
| `iqiyi-splash-close` | 爱奇艺 17.10 | Mate40（鸿蒙 4.2）快照，2026-11-06 | 广告盖在首页 `org.qiyi.android.video.MainActivity` 上，右上角按钮文字为"关闭"（朋友印象中也可能显示"跳过"，一并匹配），底部有"扭转手机 / 跳转至第三方应用或详情页面"。爱奇艺的 view id 是混淆过的（`unused_res_a`），不能按 id 匹配。插件化 App，页面名经常识别不到，故 `activities` 用 `*` |

## 暂未写规则

- **哔哩哔哩**：通用兜底已能稳定跳过（红米 K70，"跳过 5"，直接点控件有效）。目前只有广告被跳过之后的快照，等拿到广告页面的快照再补规则。

## debug 规则

`app/src/debug/assets/rules/debug.json` 只打进 debug 包，给 M1 的模拟广告页用，用来在模拟器上验证规则引擎和 `clickBy: gesture`。
