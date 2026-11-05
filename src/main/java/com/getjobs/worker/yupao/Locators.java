package com.getjobs.worker.yupao;

/**
 * 鱼泡直聘网页元素定位器
 * 集中管理所有页面元素的定位表达式。
 *
 * ⚠️ 重要说明：
 * 鱼泡直聘（www.yupao.com/web/）是 React + antd 的 SPA，页面的 class 名
 * 大概率是构建产物哈希（形如 css-1x2y3z），不能用固定 class 定位。
 * 以下选择器基于「语义化角色 + 文本」策略编写，需要在真实登录环境下
 * 用浏览器 DevTools 校准后才能稳定工作。
 *
 * 校准方法：
 * 1. 启动后端，触发 /api/yupao/login 打开鱼泡登录页
 * 2. 微信扫码登录后进入 www.yupao.com/web/ 首页
 * 3. 在 DevTools 中依次检查下列选择器是否能命中目标元素，按需修改
 */
public class Locators {
    // ---------- 通用 ----------
    /** 已登录标志：顶部用户头像/昵称区域（未登录时是"登录/注册"按钮） */
    public static final String LOGIN_INDICATOR = "[class*='user'], [class*='avatar'], [class*='loginInfo']";

    /** 登录入口按钮（未登录时显示） */
    public static final String LOGIN_ENTRY = "a[href*='login'], [class*='login'], button:has-text('登录')";

    // ---------- 搜索页 ----------
    /** 首页搜索输入框 */
    public static final String SEARCH_INPUT = "input[placeholder*='搜索'], input[placeholder*='岗位'], input[type='text']";

    /** 搜索按钮 */
    public static final String SEARCH_BUTTON = "button:has-text('搜索')";

    /** 岗位卡片容器（列表页） */
    public static final String JOB_CARDS = "[class*='jobCard'], [class*='job-item'], [class*='jobList'] > div";

    /** 岗位卡片内的「打招呼/立即沟通/报名」按钮 */
    public static final String JOB_APPLY_BUTTON = "button:has-text('打招呼'), button:has-text('立即沟通'), button:has-text('报名'), button:has-text('联系')";

    /** 岗位卡片内的薪资文本（用于薪资过滤） */
    public static final String JOB_SALARY_TEXT = "[class*='salary'], [class*='price'], [class*='money']";

    /** 岗位卡片内的标题/职位名 */
    public static final String JOB_TITLE_TEXT = "[class*='title'], [class*='jobName'], [class*='position']";

    /** 分页：下一页 */
    public static final String NEXT_PAGE = "li.ant-pagination-next button, button:has-text('下一页'), [class*='next']";

    /** 分页容器 */
    public static final String PAGINATION_BOX = ".ant-pagination, [class*='pagination']";

    // ---------- 聊天/沟通 ----------
    /** 聊天输入框 */
    public static final String CHAT_INPUT = "textarea, [class*='chat'] input[type='text']";

    /** 聊天发送按钮 */
    public static final String CHAT_SEND_BUTTON = "button:has-text('发送'), [class*='send']";

    /** 聊天窗口关闭按钮 */
    public static final String CHAT_CLOSE = "[class*='close'], [aria-label='Close'], [class*='modal'] .ant-modal-close";

    /** 弹窗确认按钮（如"确认发送"、"确定"） */
    public static final String DIALOG_CONFIRM = ".ant-modal-confirm-btns .ant-btn-primary, button:has-text('确定'), button:has-text('确认')";
}
