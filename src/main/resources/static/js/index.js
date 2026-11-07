/* ==========================================================
 * 首页逻辑
 * ----------------------------------------------------------
 * 职责：
 *   1. 三套表单（登录 / 注册 / 忘记密码）切换
 *   2. 验证码：加载时拉取、点击刷新、提交失败后重新拉取、加载中显示旋转动画
 *   3. 登录：POST /api/user/login → 存 token 跳 game.html
 *   4. 注册：POST /api/user/register → 成功后清空表单并切回登录
 *   5. 忘记密码：POST /api/user/forgot-password（无需验证码）
 *   6. 提示区：成功提示 3 秒自动消失，错误提示常驻
 *   7. 错误字段表单控件红框高亮
 *   8. 提交时禁用按钮并显示"登录中…/注册中…/提交中…"
 *   9. 自动聚焦 + Enter 跳到下一个输入框
 *  10. 密码显示/隐藏切换
 *  11. 手机号含非数字时标红并提示
 *
 * 关键约定（与后端对齐）：
 *   - 验证码是全服单例，任何一次获取都会覆盖旧值
 *     → 每次切到登录/注册面板、以及提交失败后都必须重新拉取
 *   - 后端所有接口 HTTP 状态都是 200，业务结果看 body.code
 *   - 前端只做"非空 + 两次密码一致"校验，字段规则以后端 message 为准
 * ========================================================== */

(function () {
    'use strict';

    // ---------- 0. 已登录则直接进游戏主页，避免重复登录 ----------
    if (FG.getToken()) {
        location.replace('game.html');
        return;
    }

    // ---------- 1. DOM 引用 ----------
    const panels = {
        login: document.getElementById('login-panel'),
        register: document.getElementById('register-panel'),
        forgot: document.getElementById('forgot-panel')
    };
    const messageEl = document.getElementById('auth-message');

    // 成功提示的自动消失定时器（避免多次提示时定时器叠加）
    let successTimer = null;

    /** 按 id 读取输入框的值并去掉首尾空格 */
    function val(id) {
        const el = document.getElementById(id);
        return el ? el.value.trim() : '';
    }

    /** 设置输入框的值 */
    function setVal(id, value) {
        const el = document.getElementById(id);
        if (el) el.value = value;
    }

    // ---------- 1.1 注册头像（可选） ----------
    // 选图后在浏览器里压缩成最长边 128px 的 JPEG data URL，
    // 注册成功时按用户名存进 localStorage（fg_avatar_用户名）。

    /** 当前选择的头像 data URL，未选择为空串 */
    let avatarData = '';

    /** 头像最长边像素（压缩交给 FG.compressImage，文件上限也在那里校验） */
    const AVATAR_MAX_EDGE = 128;

    /** 清空头像选择并恢复占位符 */
    function resetAvatar() {
        avatarData = '';
        const preview = document.getElementById('avatar-preview');
        const placeholder = document.getElementById('avatar-placeholder');
        const removeBtn = document.getElementById('btn-avatar-remove');
        const input = document.getElementById('register-avatar');
        if (preview) {
            preview.src = '';
            preview.classList.add('hidden');
        }
        if (placeholder) placeholder.classList.remove('hidden');
        if (removeBtn) removeBtn.classList.add('hidden');
        if (input) input.value = '';
    }

    /** 显示头像预览 */
    function showAvatarPreview(dataUrl) {
        avatarData = dataUrl;
        const preview = document.getElementById('avatar-preview');
        const placeholder = document.getElementById('avatar-placeholder');
        const removeBtn = document.getElementById('btn-avatar-remove');
        preview.src = dataUrl;
        preview.classList.remove('hidden');
        placeholder.classList.add('hidden');
        removeBtn.classList.remove('hidden');
    }

    /** 把图片文件压缩成头像 data URL（实际压缩逻辑在 FG.compressImage 公共函数里） */
    function compressAvatar(file, onSuccess, onError) {
        FG.compressImage(file, AVATAR_MAX_EDGE, onSuccess, onError);
    }

    // ---------- 2. 提示区 ----------

    /**
     * 显示提示信息
     * @param {string} text - 提示文字
     * @param {string} type - error / success / info
     *
     * 规则：success 类型 3 秒后自动消失；error 常驻，直到下一次操作
     */
    function showMessage(text, type) {
        if (successTimer) {
            clearTimeout(successTimer);
            successTimer = null;
        }

        messageEl.textContent = text || '';
        messageEl.className = 'message-area ' + (type || 'info');

        if (type === 'success' && text) {
            successTimer = setTimeout(function () {
                messageEl.textContent = '';
                messageEl.className = 'message-area';
                successTimer = null;
            }, 3000);
        }
    }

    /** 清空提示 */
    function clearMessage() {
        if (successTimer) {
            clearTimeout(successTimer);
            successTimer = null;
        }
        messageEl.textContent = '';
        messageEl.className = 'message-area';
    }

    // ---------- 3. 错误字段红框 ----------

    /** 给指定输入框加红框 */
    function markError(id) {
        const el = document.getElementById(id);
        if (el) el.classList.add('input-error');
    }

    /** 清除某个面板内所有红框 */
    function clearFieldErrors(panel) {
        panel.querySelectorAll('.input-error').forEach(function (el) {
            el.classList.remove('input-error');
        });
    }

    /**
     * 按后端返回的 message 关键词定位出错字段并标红
     * @param {string} prefix - 字段前缀：login / register / forgot
     * @param {string} message - 后端提示语
     *
     * 关键词顺序很重要：先匹配更具体的"验证码/手机号"，
     * 再匹配"用户名/账号"，最后才是"密码"（"密码错误"等泛化词）。
     */
    function markErrorByMessage(prefix, message) {
        const msg = message || '';

        if (msg.indexOf('验证码') >= 0) {
            markError(prefix + '-captcha');
            return;
        }
        if (msg.indexOf('手机号') >= 0) {
            markError(prefix + '-phone');
            return;
        }
        if (msg.indexOf('用户名') >= 0 || msg.indexOf('账号') >= 0) {
            markError(prefix + '-username');
            return;
        }
        if (msg.indexOf('密码') >= 0) {
            // 注册/忘记密码有两个密码框，一并标红
            markError(prefix + '-password');
            markError(prefix + '-password2');
        }
    }

    // ---------- 4. 提交状态（按钮禁用 + 文案） ----------

    /**
     * 切换表单提交中的状态
     * @param {HTMLFormElement} form - 表单
     * @param {boolean} busy - true 表示提交中
     * @param {string} busyText - 提交中的按钮文案，如"登录中…"
     */
    function setSubmitting(form, busy, busyText) {
        const btn = form.querySelector('button[type="submit"]');
        if (!btn) return;

        if (busy) {
            // 首次进入时记住原始文案
            if (!btn.dataset.originalText) {
                btn.dataset.originalText = btn.textContent;
            }
            btn.disabled = true;
            btn.textContent = busyText;
        } else {
            btn.disabled = false;
            btn.textContent = btn.dataset.originalText || btn.textContent;
        }
    }

    // ---------- 5. 面板切换 ----------

    /**
     * 切换到指定表单面板
     * @param {string} name - login / register / forgot
     *
     * 说明：切到登录或注册面板时会重新拉取验证码，
     *      因为后端验证码是全服单例，旧值可能已被其他请求覆盖。
     */
    function showPanel(name) {
        Object.keys(panels).forEach(function (key) {
            // 用 hidden 类控制显隐（style.css 中定义 display:none !important）
            panels[key].classList.toggle('hidden', key !== name);
        });

        clearMessage();
        // 切换面板时清掉上一面板留下的红框
        document.querySelectorAll('.input-error').forEach(function (el) {
            el.classList.remove('input-error');
        });

        if (name === 'login' || name === 'register') {
            loadCaptcha();
        }

        // 自动聚焦该面板第一个输入框
        const firstInput = panels[name].querySelector('input');
        if (firstInput) {
            // 放到下一轮事件循环，确保元素已从 display:none 变为可见
            setTimeout(function () {
                firstInput.focus();
            }, 0);
        }
    }

    // ---------- 6. 验证码 ----------

    /**
     * 拉取验证码并渲染到所有验证码展示框
     * 接口：GET /api/user/captcha（匿名，needAuth=false）
     *
     * 加载中显示旋转的 ⟳（CSS 动画），拿到结果后替换为验证码文本。
     */
    async function loadCaptcha() {
        const boxes = document.querySelectorAll('[data-captcha-refresh]');

        boxes.forEach(function (box) {
            box.classList.add('loading');
            box.innerHTML = '<span class="spin">⟳</span>';
        });

        try {
            // 返回的 data 就是验证码字符串，如 "aB3xY"
            const code = await FG.request('GET', '/api/user/captcha', null, false);
            boxes.forEach(function (box) {
                box.classList.remove('loading');
                box.textContent = code;
            });
        } catch (e) {
            boxes.forEach(function (box) {
                box.classList.remove('loading');
                box.textContent = '点此重试';
            });
            showMessage(e.message || '验证码获取失败，请点击验证码区域重试', 'error');
        }
    }

    // ---------- 7. 登录 ----------

    async function onLogin(event) {
        event.preventDefault();
        const form = event.target;
        if (form.dataset.busy === '1') return;   // 双保险：按钮已禁用，这里再挡一次

        clearMessage();
        clearFieldErrors(panels.login);

        const username = val('login-username');
        const password = val('login-password');
        const captcha = val('login-captcha');

        // 前端只做非空校验，并给空字段标红
        if (!username || !password || !captcha) {
            showMessage('请填写用户名、密码和验证码', 'error');
            if (!username) markError('login-username');
            if (!password) markError('login-password');
            if (!captcha) markError('login-captcha');
            return;
        }

        form.dataset.busy = '1';
        setSubmitting(form, true, '登录中…');
        FG.showLoading();
        try {
            // 成功时 data 就是 token 字符串
            const token = await FG.request('POST', '/api/user/login', {
                username: username,
                password: password,
                captcha: captcha
            }, false);

            // 保存登录态后跳游戏主页
            FG.setToken(token);
            FG.setUsername(username);
            location.href = 'game.html';
        } catch (e) {
            // 密码错误 / 账号锁定 403 / 验证码错误 都在这里
            // 后端 message 已包含"剩余 N 次机会"或"账号已锁定，请联系客服 400-610-5500"
            showMessage(e.message, 'error');
            markErrorByMessage('login', e.message);

            // 验证码一次性使用，失败后必须重新拉取并清空输入
            setVal('login-captcha', '');
            loadCaptcha();
        } finally {
            FG.hideLoading();
            setSubmitting(form, false);
            delete form.dataset.busy;
        }
    }

    // ---------- 8. 注册 ----------

    async function onRegister(event) {
        event.preventDefault();
        const form = event.target;
        if (form.dataset.busy === '1') return;

        clearMessage();
        clearFieldErrors(panels.register);

        const username = val('register-username');
        const password = val('register-password');
        const password2 = val('register-password2');
        const phone = val('register-phone');
        const captcha = val('register-captcha');

        if (!username || !password || !password2 || !phone || !captcha) {
            showMessage('请填写全部注册信息', 'error');
            if (!username) markError('register-username');
            if (!password) markError('register-password');
            if (!password2) markError('register-password2');
            if (!phone) markError('register-phone');
            if (!captcha) markError('register-captcha');
            return;
        }
        // 两次密码一致性由前端兜住，其余规则交给后端
        if (password !== password2) {
            showMessage('两次输入的密码不一致', 'error');
            markError('register-password2');
            // 提示后刷新验证码：清空已输入的验证码并重新拉取，避免用户面对已失效的旧码
            setVal('register-captcha', '');
            loadCaptcha();
            return;
        }

        form.dataset.busy = '1';
        setSubmitting(form, true, '注册中…');
        FG.showLoading();
        try {
            // 成功时 data 为空，FG.request 会返回 message 提示语
            const msg = await FG.request('POST', '/api/user/register', {
                username: username,
                password: password,
                phone: phone,
                captcha: captcha
            }, false);

            // 注册成功：清空注册表单，避免下次进入注册面板时残留旧信息
            document.getElementById('register-form').reset();

            // 头像按用户名存本地（仅本机可见，清缓存会丢失）
            if (avatarData) {
                FG.setAvatar(username, avatarData);
            }
            resetAvatar();

            // 回登录面板并回填用户名，减少重复输入
            setVal('login-username', username);
            setVal('login-password', '');
            setVal('login-captcha', '');
            showPanel('login');
            // 注意：showPanel 内部会 clearMessage，所以成功提示必须放在其后
            showMessage(msg || '注册成功，请登录', 'success');
        } catch (e) {
            showMessage(e.message, 'error');
            markErrorByMessage('register', e.message);
            // 注册用的验证码同样一次性，失败后刷新
            setVal('register-captcha', '');
            loadCaptcha();
        } finally {
            FG.hideLoading();
            setSubmitting(form, false);
            delete form.dataset.busy;
        }
    }

    // ---------- 9. 忘记密码 ----------

    async function onForgot(event) {
        event.preventDefault();
        const form = event.target;
        if (form.dataset.busy === '1') return;

        clearMessage();
        clearFieldErrors(panels.forgot);

        const username = val('forgot-username');
        const phone = val('forgot-phone');
        const newPassword = val('forgot-password');
        const newPassword2 = val('forgot-password2');

        if (!username || !phone || !newPassword || !newPassword2) {
            showMessage('请填写全部信息', 'error');
            if (!username) markError('forgot-username');
            if (!phone) markError('forgot-phone');
            if (!newPassword) markError('forgot-password');
            if (!newPassword2) markError('forgot-password2');
            return;
        }
        if (newPassword !== newPassword2) {
            showMessage('两次输入的新密码不一致', 'error');
            markError('forgot-password2');
            return;
        }

        form.dataset.busy = '1';
        setSubmitting(form, true, '提交中…');
        FG.showLoading();
        try {
            // 该接口不需要验证码
            const msg = await FG.request('POST', '/api/user/forgot-password', {
                username: username,
                phone: phone,
                newPassword: newPassword
            }, false);

            // 成功：清空忘记密码表单，避免下次进入时残留旧信息
            document.getElementById('forgot-form').reset();

            // 回登录面板并回填用户名
            setVal('login-username', username);
            setVal('login-password', '');
            showPanel('login');
            // 注意：showPanel 内部会 clearMessage，所以成功提示必须放在其后
            showMessage(msg || '密码修改成功，请使用新密码登录', 'success');
        } catch (e) {
            showMessage(e.message, 'error');
            markErrorByMessage('forgot', e.message);
        } finally {
            FG.hideLoading();
            setSubmitting(form, false);
            delete form.dataset.busy;
        }
    }

    // ---------- 10. 事件绑定 ----------

    const forms = {
        login: document.getElementById('login-form'),
        register: document.getElementById('register-form'),
        forgot: document.getElementById('forgot-form')
    };

    forms.login.addEventListener('submit', onLogin);
    forms.register.addEventListener('submit', onRegister);
    forms.forgot.addEventListener('submit', onForgot);

    // 面板切换链接（data-goto="login|register|forgot"）
    document.querySelectorAll('[data-goto]').forEach(function (link) {
        link.addEventListener('click', function (event) {
            event.preventDefault();
            showPanel(link.getAttribute('data-goto'));
        });
    });

    // 验证码展示框：点击刷新
    document.querySelectorAll('[data-captcha-refresh]').forEach(function (box) {
        box.addEventListener('click', function () {
            loadCaptcha();
        });
    });

    // 密码显示/隐藏切换
    document.querySelectorAll('[data-pwd-toggle]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const input = btn.parentElement.querySelector('input');
            if (!input) return;

            // password ↔ text
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.textContent = show ? '🙈' : '👁';
            btn.setAttribute('title', show ? '隐藏密码' : '显示密码');
            input.focus();
        });
    });

    // 手机号：含非数字说明输错了，标红并提示，由用户自己改正
    document.querySelectorAll('input[data-phone]').forEach(function (el) {
        // 输入时即时反馈：只标红，提示用户输入非法
        el.addEventListener('input', function () {
            if (el.value && /\D/.test(el.value)) {
                el.classList.add('input-error');
            } else {
                el.classList.remove('input-error');
            }
        });

        // 失焦时给出明确的文字提示（避免每敲一个字就弹提示）
        el.addEventListener('blur', function () {
            if (el.value && /\D/.test(el.value)) {
                showMessage('手机号只能输入数字，请检查后重新输入', 'error');
            }
        });
    });

    // 用户重新输入时自动清除该字段的红框
    // 注意：手机号有专属校验（含非数字时需保持标红），这里跳过
    document.querySelectorAll('input').forEach(function (el) {
        if (el.hasAttribute('data-phone')) return;

        el.addEventListener('input', function () {
            el.classList.remove('input-error');
        });
    });

    // 注册头像：选择文件 → 压缩 → 预览；移除按钮清空
    document.getElementById('register-avatar').addEventListener('change', function (event) {
        const file = event.target.files && event.target.files[0];
        if (!file) return;
        compressAvatar(file, showAvatarPreview, function (msg) {
            resetAvatar();
            showMessage(msg, 'error');
        });
    });

    document.getElementById('btn-avatar-remove').addEventListener('click', function () {
        resetAvatar();
    });

    // Enter 跳到下一个输入框；最后一个输入框交给表单默认提交
    Object.keys(forms).forEach(function (key) {
        const form = forms[key];
        // 文件选择框不参与 Enter 跳转
        const fields = Array.prototype.slice.call(form.querySelectorAll('input'))
            .filter(function (el) {
                return el.type !== 'file';
            });

        form.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter') return;

            const index = fields.indexOf(event.target);
            // 不是最后一个输入框 → 拦截默认提交，聚焦下一个
            if (index >= 0 && index < fields.length - 1) {
                event.preventDefault();
                fields[index + 1].focus();
            }
            // 最后一个输入框：不拦截，浏览器会触发表单提交
        });
    });

    // ---------- 11. 初始化 ----------
    showPanel('login');
})();
