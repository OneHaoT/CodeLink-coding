<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { http } from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const form = ref({ account: '', password: '' })
const loading = ref(false)
const remember = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const res: any = await http.post('/auth/login', form.value)
    userStore.setAuth(res.data.accessToken, res.data.refreshToken, {
      id: res.data.user.id,
      username: res.data.user.username,
      avatar: res.data.user.avatar,
      role: res.data.user.role,
    })
    ElMessage.success('登录成功')
    const redirect = router.currentRoute.value.query.redirect as string
    router.push(redirect || '/')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <!-- 动态背景 -->
    <div class="bg-layer">
      <div class="blob b1"></div>
      <div class="blob b2"></div>
      <div class="blob b3"></div>
      <div class="grid-overlay"></div>
    </div>

    <!-- 登录卡 -->
    <div class="auth-card">
      <div class="card-brand">
        <div class="brand-mark">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <polyline points="16 18 22 12 16 6"></polyline>
            <polyline points="8 6 2 12 8 18"></polyline>
          </svg>
        </div>
        <div class="brand-name">CodeLink</div>
      </div>

      <h2 class="card-title">欢迎回来</h2>
      <p class="card-sub">登录继续你的技术之旅</p>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="field">
          <label>昵称</label>
          <div class="input-wrap">
            <el-icon class="input-ic"><User /></el-icon>
            <input v-model="form.account" type="text" placeholder="输入你的昵称" required />
          </div>
        </div>

        <div class="field">
          <div class="label-row">
            <label>密码</label>
            <a href="#" class="link" @click.prevent>忘记密码?</a>
          </div>
          <div class="input-wrap">
            <el-icon class="input-ic"><Lock /></el-icon>
            <input v-model="form.password" type="password" placeholder="••••••••" required />
          </div>
        </div>

        <label class="check">
          <input v-model="remember" type="checkbox" />
          <span>记住我</span>
        </label>

        <button type="submit" class="submit" :disabled="loading">
          <span v-if="!loading">登录</span>
          <el-icon v-else class="spinning"><Loading /></el-icon>
        </button>
      </form>

      <div class="card-foot">
        <span>还没有账号?</span>
        <router-link to="/register" class="link-bold">立即注册 →</router-link>
      </div>

      <!-- 装饰代码 -->
      <div class="code-line">
        <span class="c-kw">const</span> <span class="c-v">u</span> =
        <span class="c-f">auth</span>.<span class="c-m">login</span>(<span class="c-s"
          >'{{ form.account || 'you' }}'</span
        >)
      </div>
    </div>

    <!-- 底部说明 -->
    <div class="auth-foot">
      <span>© 2026 CodeLink · 连接开发者与优质内容</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $s-4;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #312e81 100%);
}

// --- 动态背景 ---
.bg-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;

  .blob {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    opacity: 0.5;
    animation: float 12s ease-in-out infinite;
  }

  .b1 {
    width: 500px;
    height: 500px;
    background: $brand;
    top: -100px;
    left: -100px;
    animation-delay: 0s;
  }

  .b2 {
    width: 400px;
    height: 400px;
    background: $accent;
    bottom: -50px;
    right: -50px;
    animation-delay: -4s;
  }

  .b3 {
    width: 300px;
    height: 300px;
    background: #ec4899;
    top: 40%;
    left: 60%;
    animation-delay: -8s;
  }

  .grid-overlay {
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
    background-size: 40px 40px;
  }
}

@keyframes float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(40px, -40px) scale(1.1);
  }
  66% {
    transform: translate(-30px, 30px) scale(0.9);
  }
}

// --- 登录卡 ---
.auth-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: $s-8;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  box-shadow: 0 32px 64px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(20px);
  animation: slideUp 0.5s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-brand {
  display: flex;
  align-items: center;
  gap: $s-3;
  margin-bottom: $s-6;

  .brand-mark {
    width: 44px;
    height: 44px;
    background: linear-gradient(135deg, $brand, darken($brand, 20%));
    border-radius: $r-md;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow: $sh-brand;

    svg {
      width: 24px;
      height: 24px;
    }
  }

  .brand-name {
    font-family: $font-display;
    font-size: 22px;
    font-weight: 700;
    color: $ink;
  }
}

.card-title {
  font-size: 28px;
  font-weight: 800;
  color: $ink;
  margin: 0 0 $s-1;
  letter-spacing: -0.01em;
}

.card-sub {
  font-size: $fs-base;
  color: $ink-2;
  margin: 0 0 $s-6;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: $s-4;

  .field {
    display: flex;
    flex-direction: column;
    gap: $s-2;

    label {
      font-size: $fs-sm;
      font-weight: 600;
      color: $ink;
    }

    .label-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .input-wrap {
    display: flex;
    align-items: center;
    gap: $s-2;
    padding: 0 $s-3;
    height: 44px;
    background: $surface-alt;
    border: 2px solid transparent;
    border-radius: $r-md;
    transition: all 0.15s;

    &:focus-within {
      border-color: $brand;
      background: $surface;
    }

    .input-ic {
      color: $ink-3;
      font-size: 18px;
    }

    input {
      flex: 1;
      border: none;
      background: transparent;
      outline: none;
      font-size: $fs-base;
      font-family: inherit;
      color: $ink;

      &::placeholder {
        color: $ink-4;
      }
    }
  }

  .check {
    display: flex;
    align-items: center;
    gap: $s-2;
    font-size: $fs-sm;
    color: $ink-2;
    cursor: pointer;

    input[type='checkbox'] {
      width: 16px;
      height: 16px;
      accent-color: $brand;
    }
  }

  .submit {
    height: 48px;
    border: none;
    border-radius: $r-md;
    background: linear-gradient(135deg, $brand, darken($brand, 15%));
    color: #fff;
    font-size: $fs-lg;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
    margin-top: $s-2;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: $sh-brand;
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }
    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .spinning {
      animation: spin 0.8s linear infinite;
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.link {
  font-size: $fs-sm;
  color: $brand;
  text-decoration: none;
  font-weight: 500;
}

.link-bold {
  color: $brand;
  font-weight: 600;
  text-decoration: none;
}

.card-foot {
  text-align: center;
  margin-top: $s-5;
  font-size: $fs-sm;
  color: $ink-3;

  .link-bold {
    margin-left: 4px;
  }
}

// --- 装饰代码 ---
.code-line {
  margin-top: $s-5;
  padding: $s-2 $s-3;
  background: $bg-code;
  border-radius: $r-sm;
  font-family: $font-mono;
  font-size: $fs-xs;
  color: #e2e8f0;
  text-align: center;

  .c-kw {
    color: #c084fc;
  }
  .c-v {
    color: #38bdf8;
  }
  .c-f {
    color: #34d399;
  }
  .c-m {
    color: #60a5fa;
  }
  .c-s {
    color: #fca5a5;
  }
}

.auth-foot {
  position: absolute;
  bottom: $s-6;
  color: rgba(255, 255, 255, 0.4);
  font-size: $fs-sm;
  z-index: 1;
}
</style>
