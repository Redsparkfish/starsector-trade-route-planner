# Starsector 跑商规划 — 开发辅助备忘

对照本机安装：`D:\games\starsector`（**0.98a-RC8**，JRE 17）。  
需求与硬限制以仓库根目录 `AGENTS.md` 为准；本文是 API / 机制的持久化笔记，实现时先读 §5 硬限制再写代码。  
架构见 `AGENTS.md` §3。

**权威源（本地，不要去网上翻）：**

| 路径 | 内容 |
|------|------|
| `starsector-core/starfarer.api.zip` | 公开 API 源码（Java 接口 + 部分 `impl`） |
| `starsector-core/starfarer.api.jar` | 编译后的 API，模组编译 classpath |
| `starsector-core/data/campaign/econ/economy.json` | `defaultTariff: 0.3` |
| `starsector-core/data/campaign/submarkets.csv` | 开市 / 黑市 / 军事 / 仓库 |
| `starsector-core/data/campaign/commodities.csv` | 商品定义、货舱体积、`econUnit` |
| `starsector-core/data/config/settings.json` | burn / 光年像素 / 通讯中继范围 |

入口类：`com.fs.starfarer.api.Global` → `getSector()` / `getSettings()`。

---

## 1. 模组工程约定

- 生命周期：`BaseModPlugin`（`onApplicationLoad` / `onGameLoad`）。
- **优化器打 jar**，不要用 Janino 散落 `data/scripts`（复杂算法、泛型容易编不过）。
- 配置：软依赖 LunaLib（本机已装）；没有则 `data/config/settings.json`。F3 字段在 `data/config/LunaSettings.csv`。
- 代码分层：`config` / `data` / `service` / `engine` / `model` / `ui` / `exec`（见 `AGENTS.md` §3）。不要把新逻辑堆进情报类以外的游戏内部类。
- `mod_info.json` 的 `gameVersion` 与本机一致写 `0.98a-RC8`，最低兼容文档写 0.97a+。
- 本机已装、可对照行为：Stelnet（比价情报）、Nexerelin（市场数量暴增）、LunaLib。

本模组定位：**咨询式规划器**。航行用原版「设导航」；到站后可选「本站自动买卖」（走原版 `$tradeMode`，按货架尽力成交）。不连锁 Autopilot、不远程开店。

---

## 2. 经济层：价格是全局的，货架不是

### 2.1 星区里没有「统一牌价」

每种商品在**每个市场**有独立的供需价，由库存、需求、稳定度等当场算，不是一张全宇宙价目表。

食品在 A 星和 B 星可以差很多，这是设计。

### 2.2 价格数据全局可读（无需访问市场）

```text
Global.getSector().getEconomy().getMarketsCopy()
MarketAPI.getSupplyPrice(commodityId, quantity, isPlayerPrice)  // 市场卖给玩家（玩家买）
MarketAPI.getDemandPrice(commodityId, quantity, isPlayerPrice)  // 市场向玩家买（玩家卖）
```

- `quantity` 会改变报价（滑价）。**禁止**「单价 × 数量」代替整笔报价。
- 第三个参数叫 `isPlayerPrice`，**不是**黑市开关。原版 `PriceUpdate` 一律传 `true`。
- 玩家技能/加成走 player price 通道；关税是否已含在返回值里，实现时必须对着交易界面实测一次，未含则自行乘 `SubmarketAPI.getTariff()`。

本体自己也在用这套价：`PriceUpdate`、`TradeInfoUpdateEvent`（本地/远程「异常价」通讯）。玩家界面故意不全表列出，所以看起来像「没有全局价格」；**模拟层是有的**。

Stelnet 的 `SupplyPrice` / `DemandPrice` 就是直接调上面两个方法。

### 2.3 货架库存不是全局的（硬限制）

```text
BaseSubmarketPlugin.getCargo()
  → cargo == null 时 createCargo(true)，空仓（未访问市场禁止调用）
BaseSubmarketPlugin.getCargoNullOk()
  → 未开门为 null；已访问才有剩余件数
OpenMarketPlugin.updateCargoPrePlayerInteraction()
  → 仅玩家打开交易时刷新货架
```

未访问市场调用 `getCargo()` 会**造出空仓并可能写入存档**。规划器只用 `getCargoNullOk()` 读已访问剩余，从未访问则不当 0 件。

`getApproximateStockpileLimit` 是确定基数 \(L_0\)（不含稳定度、不含随机；进口 0.1 / 产量 0.4 / 余量 1.0 / 缺口 0.2）。开门实际上限是 \(L_0 \times U \times S\)，\(E[U]=1\)。规划器对 \(L\) 的无偏点估计，以及未访问时的买上限：

```text
S_open  = 0.25 + 0.75 × (stability / 10)
S_black = 0.25 + 0.75 × (1 − stability / 10)
E[L]    = floor(L0 × S)     // 未访问
```

已访问：只读 `getCargoNullOk()`，按原版 `addAndRemoveStockpiledResources` 把剩余靠向 \(E[L]\)（低于则 \(E[L]/30\) 件/天补，高于则 \((q-E[L])\times 2/30\) 件/天收）。开市非法品不加货，买上限 0。不要改进口系数去贴「看见的货架」——那是对 \(L\) 有偏。

卖出**没有件数硬顶**：游戏用 `getDemandPrice(k, qty)` 滑价，不限制能卖多少。规划器只受出发地买上限、货舱、现金、以及批量报价利润 > 0 约束。开市非法品不能在开市卖。不要用 `deficit` / dest \(E[L]\) 当卖上限。

| 含义 | API / 公式 |
|------|-----|
| 能低价买的过剩量（报价便宜段，不是货架） | `CommodityOnMarketAPI.getExcessQuantity()` |
| 能高价卖的缺口量 | `getDeficitQuantity()` |
| 开市库存上限基数 \(L_0\) | `OpenMarketPlugin.getApproximateStockpileLimit(com)` |
| 期望货架 / 计划买卖 | `ShelfQuantityEstimator`（未访问 \(L_0\times S\)，已访问 leftover） |
| 商品体积 | `CommoditySpecAPI.getCargoSpace()` |
| 经济单位（批量计价常用） | `getEconUnit()` |

规划数量必须当**估计**，再乘 `qtySafetyMargin`（默认 0.9），UI 不得写成「货架上一定有这么多」。

### 2.4 市场过滤（必须）

纳入候选前：

- `market.isInEconomy() == true`
- `!market.isHidden()`（隐藏市场被情报扫到等于剧透）
- `!market.isPlanetConditionMarketOnly()`
- 商品：`!spec.isNonEcon()` 且 `!spec.isMeta()`（排除调查数据、蓝图、AI 核心当大宗货）

可达性另算：敌对 + 开应答器才能用开市；关应答器（`CoreUITradeMode.SNEAK`）开市禁用、黑市可用。军事市场要声望（`MilitarySubmarketPlugin`，通常至少 `RepLevel.FAVORABLE`）。

### 2.5 原版可交易大宗（`commodities.csv`）

规划默认只扫经济大宗（约 16 种）：`supplies`, `fuel`, `food`, `organics`, `volatiles`, `ore`, `rare_ore`, `metals`, `rare_metals`, `heavy_machinery`, `domestic_goods`, `organs`, `drugs`, `hand_weapons`, `luxury_goods`, `lobster`。  
`crew` / `marines` 占人员舱不占货舱，一般不当跑商货。`fuel` 占燃料舱。

### 2.6 成交会改后续价格

`BaseSubmarketPlugin.reportPlayerMarketTransaction` → `CommodityOnMarketAPI.addTradeMod*`。  
路线是**计算时刻的快照**；玩家跑完第一站，后面站的价和量都会变。不要承诺全程数字不变。

不要从后台脚本调 `EconomyAPI.nextStep()` / `doubleStep()` / `tripleStep()`（API 写明仅 UI 交互，可能超过一帧）。

---

## 3. 关税与黑市

| 市场 | 关税 | 非法品 |
|------|------|--------|
| 开市 `OpenMarketPlugin` | `market.getTariff()`，默认 **0.3，买卖双边** | `market.isIllegal()` 则不可交易 |
| 黑市 `BlackMarketPlugin` | **固定 0** | `isIllegalOnSubmarket` 恒 false |
| 军事 `MilitarySubmarketPlugin` | 走基类关税 | 部分军用货；要声望；sneak 模式禁用 |

`economy.json`：`"defaultTariff": 0.3`。  
自由港 `isFreePort()` 会改变非法品判定，关税仍以该市场 `getTariff()` 为准。

每个势力可在情报「规划设置」里单独开关**开市**与**黑市**（勾选为草稿，确认后写入当前存档）。默认：`blackMarketFactions`（海盗、卢德左径）仅黑市，其余仅开市。两边都开则先黑市采购/出货，货舱、现金仍有余且税后有利可图再走开市；两边都关则不与该势力交易。规划器只提示，不自动关应答器。

旧的全局 `marketMode`（`LEGAL_ONLY` / `ALLOW_BLACK_MARKET`）仅用于给还没有这份表的旧存档做一次迁移。

---

## 4. 舰队状态

玩家舰队：`Global.getSector().getPlayerFleet()`。

| 需求 | API |
|------|-----|
| 现金 | `fleet.getCargo().getCredits()` |
| 货舱上限 / 剩余 | `getMaxCapacity()` / `getSpaceLeft()` |
| 已用货舱 | `getSpaceUsed()` |
| 燃料 / 油箱 | `getFuel()` / `getMaxFuel()` |
| 人员上限 | `getMaxPersonnel()` |
| 有效 burn | `fleet.getFleetData().getBurnLevel()` |
| 最慢船 burn | `getMinBurnLevel()` |
| 日补给 | `fleet.getLogistics().getTotalSuppliesPerDay()` |
| **超空间燃料 / 光年** | `getLogistics().getFuelCostPerLightYear()` |

**没有**「日油耗」这一等一的一等公民字段。  
`Misc.getFuelPerDay(fleet, burn)` 只是用「当前 burn 对应的 ly/天 × 每光年油耗」反推出来的，路线成本请直接用光年。

同步：改完货舱/编队后如需最新容量，`fleet.forceSync()`。

---

## 5. 旅行时间与费用

`settings.json`（本机）：

- `speedPerBurnLevel`: **20**（像素/实时秒）
- `baseTravelSpeed`: 0
- `unitsPerLightYear`: **2000**
- 注释：约 **10 实时秒 = 1 游戏日**

官方换算（`Misc`）：

```text
distLY = Misc.getDistanceLY(from, to)           // 超空间坐标
lyPerDay = Misc.getLYPerDayAtBurn(fleet, burn)
hyperspaceDays ≈ distLY / lyPerDay
fuel = distLY * logistics.getFuelCostPerLightYear()
supplyCost = days * logistics.getTotalSuppliesPerDay() * P_supply
```

星系内：

```text
JumpPointAPI jp = Misc.findNearestJumpPointTo(entity);
像素距离 / burn速度 → 天数   // 近似；行星在轨道上动
```

**不要建模（标「预计」即可）：** 滑流、持续燃烧、超空间风暴、轨道相位、横断跃迁技能。

星系内燃油走 `getFuelUseNormalMult()`，通常远小于超空间；**不要**把超空间「每光年油耗」套到星系内腿上。

途中加油：`F_max` 不是一次扣完全程。货舱可能要留燃料。规划器至少保证「跳到下一站油够」，能加油再放大搜索。

补给/燃料保留（`reserveSupplyDays` / `reserveFuelDays`，默认 100）：不把保留量拿去卖掉。燃料天数用 `Misc.getFuelPerDay(fleet, burn)`（超空间日耗折算），不是星系内日耗。保留量封顶油箱。补给是经济大宗，规划可以买卖；短少时仍预留货舱并到站补到保留线。

---

## 6. 导航与情报 UI

### 6.1 只能设一个航点

```text
CampaignUIAPI ui = Global.getSector().getCampaignUI();
ui.layInCourseForNextStep(entity);   // 官方 Autopilot，单一终点
ui.clearLaidInCourse();
ui.getUltimateCourseTarget();
```

「一键导航」= 下一站（或第一站）。  
禁止：多停 Autopilot 链、远程未抵达开店。半自动一站见 §6.4。

情报里可用 `IntelInfoPlugin.ArrowData` 画剩余航段，`getMapLocation()` 把当前站标在星图上。到站后再点一次「下一站」。

### 6.2 Intel 面板（已验证路径）

- 基类：`com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin`
- 注册：`Global.getSector().getIntelManager().addIntel(plugin)`
- 大面板：`hasLargeDescription()` + `createLargeDescription(CustomPanelAPI, w, h)`
- 按钮：`TooltipMakerAPI.addButton` → `buttonPressConfirmed(buttonId, IntelUIAPI)`
- 刷新：`ui.updateUIForItem(this)` / `ui.recreateIntelUI()`
- 颜色：`Misc.getPositiveHighlightColor()` / `getNegativeHighlightColor()`
- 标签：`getIntelTags`；原版有 `Trade` 等 tag（`data/config/tag_data.json`）

Stelnet / Captain's Log 都是这套。本机 Stelnet 证明 `CustomPanelAPI` 大情报 + 按钮可用。

计算必须在**战役主线程**。无界 DFS 会卡死情报界面。默认最多 3–4 停、先筛价差候选、按钮上加计算时限。原版约 50 市场 × 16 商品；Nexerelin 可 >100 市场。

### 6.3 战役旅行地图 HUD（非官方一等接口）

没有「往战役 HUD 加按钮」的公开 API。本模组用反射挂到当前 `CampaignState` 的 `getScreenPanel()`（与 Console Commands / MagicLib 相同），再 `Global.getSettings().createCustom` + `UIPanelAPI.addComponent`。不要 import `com.fs.starfarer.campaign.CampaignState` / `com.fs.state.AppDriver`（Gradle 只有 `starfarer.api.jar`）。

- 脚本：`EveryFrameScript` + `addTransientScript`（读档注册，不写入存档）
- 显示：战役层、未开对话/菜单、`getCurrentCoreTab() == null`、未 `isHideUI()`
- 按钮：战役层没有 `IntelUIAPI.buttonPressConfirmed`，只轮询 `ButtonAPI.isChecked()` 后立刻 `setChecked(false)`。不要同时 override `buttonPressed`：一次点击会进两条路径，长计算会绕过 250ms 防抖连跑两次。`calculateRoute` 另有重入锁和 750ms 冷却。
- 打开情报：`showCoreUITab(CoreUITabId.INTEL, intelPlugin)`
- 显示开关：本存档 `hudVisible`（默认开）。HUD 标题栏 `x` 关闭；情报「切换HUD显示」翻转；「计算新路线」会重新打开。

HUD 只放紧凑作业单（预计净利，天数，净利/天，下一站买卖，计算，导航，本站自动买卖，详情）。**没有**「标记抵达」（该按钮只在情报大面板）。全程分站仍在情报大面板。

### 6.4 半自动一站执行

按钮「本站自动买卖」只在**已经抵达**当前下一站时停靠：若原版已经打开据点对话则接着用，否则 `showInteractionDialog`（走 `MarketPostOpen` / `$tradeMode`）→ 选 `marketOpenCoreUI` → 按当场货架改 `CargoAPI` 并 `reportPlayerMarketTransaction` → 关对话。成功后作业单翻页；可选再 `layInCourseForNextStep` 下一站。未串联导航时 `setPaused(true)`。

航行只用「设导航」调用 `layInCourseForNextStep`，之后完全是原版 Autopilot；点地图、改航线、暂停都按原版。未到站点「本站自动买卖」会提示先飞过去，不会监视航线。可选「到站后自动买卖」在 `StopExecutorScript`（暂停时也跑）里对当前下一站自动 `start`，同一趟抵达只尝试一次。

- `$tradeMode == NONE`（`IsSoughtByPatrols` 等）必须中止，不要成交。
- 数量是 `min(计划, 货架, 舱, 钱)`，不是规划横幅保证。
- 读档不续跑状态机。作业单不在「人一进圈」时翻页。
- 「设导航」仍只铺航线。

### 6.5 游戏内文案可用字符

原版 UI 字体（Orbitron / Insignia / **Victor**）字库很窄。HUD 明确调用了 `setParaFontVictor14` / `setButtonFontVictor10`，缺字形会变成方框或空白。中文补丁对汉字和常见全角标点（`，。：；！？（）`）一般有回退，**不要假设**拉丁补充、希腊字母、箭头、直角引号也能显示。

**进游戏的字符串**（情报 `addPara` / 按钮、HUD、战役 `addMessage`、LunaLib F3 的 `fieldName` / `fieldDescription`）只用：ASCII、汉字、全角 `（）`。代码注释和本文不受限。

| 不要写 | 改成 | 说明 |
|--------|------|------|
| `·`（U+00B7 间隔号） | 全角逗号 `，` | 并列分隔不要用 `/`；比率和公式可以用 `/` |
| `「」` 直角引号 | `"` 或全角 `（）` | 常被叫做半角/直角引号，Victor 没有 |
| `→` `←` 等箭头 | `->` 或汉字「到」 | Arrows 区段 |
| `α` 等希腊字母 | `alpha` | 设置项「定位权重」也写 `alpha`，不要写 α |
| `×` | `x` | 拉丁补充乘号 |
| `∞` | `无限` | |
| `……` `…` | `...` | |
| `≈` | `~` | |

不要用半角中括号 `[]` 装饰按钮名（CSV 里也不要用 `\"` 嵌套引号；LunaSettings 用全角括号包按钮名）。并列项用全角逗号 `，`。比率写「净利/天」，公式写 `回路利润 / (回路天数 + alpha x 定位天数)`。

---

## 7. 推荐取数伪代码

```text
// 快照市场（计算按钮时，主线程，一次性）
for market in economy.getMarketsCopy():
    if hidden or not inEconomy or planetConditionOnly: skip
    for com in market.getAllCommodities():
        spec = com.getCommodity()
        if spec.isNonEcon() or spec.isMeta() or spec.isPersonnel(): skip
        buyQuote  = market.getSupplyPrice(id, qty, true)
        sellQuote = market.getDemandPrice(id, qty, true)
        tariff    = market.getSubmarket("open_market").getTariff()  // 或缺则 market.getTariff()
        blackTariff = 0
        excess / deficit / approxStock = ...

// 舰队
fleet = sector.getPlayerFleet()
W0 = cargo.credits
Cmax = cargo.spaceLeft   // 或 maxCapacity - 预留
Fmax = cargo.maxFuel - 预留
burn = fleet.fleetData.burnLevel
supplyPerDay = fleet.logistics.totalSuppliesPerDay
fuelPerLY = fleet.logistics.fuelCostPerLightYear
```

开市净利（若实测确认 API 价为税前）：

```text
buyCost  = buyQuote * (1 + tariff)
sellRev  = sellQuote * (1 - tariff)
```

黑市：`tariff = 0`，非法品可交易。敌对开市在 sneak 下不可用。

---

## 8. 和本机已装模组的边界

| 模组 | 做什么 | 本模组不要重复 |
|------|--------|----------------|
| Stelnet | 全市场比价、单商品买点/卖点 | 不重做价目表；做多停 + 货舱背包 + 油/补给后的 Credits/Day |
| Nexerelin | 随机星区、市场变多 | 搜索必须有停数和耗时上限 |
| LunaLib | 游戏内设置 | 软依赖；T_max、是否闭环、合法/黑市开关 |

---

## 9. 实现时仍需对着游戏点一次的事项

1. `getSupplyPrice(..., true)` 返回值是否已含开市关税（对照交易界面合计行）。  
2. 开市卖出是「卖价 × (1−关税)」还是界面另扣一行。  
3. `getExcessQuantity` 的单位是「件数」还是经济档；与 `econUnit`、货架件数如何换算。  
4. 玩家在星系内 vs 超空间时，到目标市场的第一段距离怎么拼 jump point。

这四条不写死在代码常量里。计算路线时有 `TradeRoutePlanner firstLeg:` 日志。对着交易界面合计行测完再改 `assumeApiPriceExcludesTariff` 默认值或数量换算。

---

## 10. 不要做的事

- 远程 `getCargo()` 当真实库存（造空仓）；已访问剩余只用 `getCargoNullOk()`  
- `DailyFuel × 天数` 当超空间油费  
- 把 `isPlayerPrice` 当成黑市布尔  
- 无剪枝全排列 5+ 停（尤其 Nex）  
- 给玩家舰队排全程 Autopilot / 远程未抵达自动交易（到站后的一站 `StopExecutor` 除外）  
- 把隐藏市场算进路线  
- 从 `EveryFrameScript` 里 `economy.nextStep()`  
- 在 UI 上把「预计」写成精确到小时的到站钟点  
