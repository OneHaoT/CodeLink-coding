<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Check } from '@element-plus/icons-vue'
import { http } from '@/api/request'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)
const steps = computed(() => [
  { label: '昵称', ok: form.value.username.length >= 2 },
  { label: '密码', ok: form.value.password.length >= 6 },
])

async function handleRegister() {
  loading.value = true
  try {
    await http.post('/auth/register', form.value)
    ElMessage.success('注册成功，快去登录吧！')
    router.push('/login')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="bg-layer">
      <div class="blob b1"></div>
      <div class="blob b2"></div>
      <div class="grid-overlay"></div>
    </div>

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

      <h2 class="card-title">创建账号</h2>
      <p class="card-sub">开启你的技术分享之旅</p>

      <!-- 步骤进度 -->
      <div class="steps">
        <div v-for="(s, i) in steps" :key="i" class="step" :class="{ done: s.ok }">
          <div class="step-ic">
            <el-icon v-if="s.ok"><Check /></el-icon>
            <span v-else>{{ i + 1 }}</span>
          </div>
          <span class="step-label">{{ s.label }}</span>
          <div v-if="i < steps.length - 1" class="step-line"></div>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="field">
          <label>昵称</label>
          <div class="input-wrap">
            <el-icon class="input-ic"><User /></el-icon>
            <input v-model="form.username" type="text" placeholder="2-32 个字符" required />
          </div>
        </div>

        <div class="field">
          <label>密码</label>
          <div class="input-wrap">
            <el-icon class="input-ic"><Lock /></el-icon>
            <input v-model="form.password" type="password" placeholder="至少 6 位" required />
          </div>
        </div>

        <button type="submit" class="submit" :disabled="loading">
          <span v-if="!loading">创建账号</span>
          <el-icon v-else class="spinning"><Loading /></el-icon>
        </button>
      </form>

      <div class="card-foot">
        <span>已有账号?</span>
        <router-link to="/login" class="link-bold">立即登录 →</router-link>
      </div>
    </div>

    <div class="auth-foot">© 2026 CodeLink · 连接开发者与优质内容</div>
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
    right: -100px;
  }
  .b2 {
    width: 400px;
    height: 400px;
    background: #ec4899;
    bottom: -50px;
    left: -50px;
    animation-delay: -6s;
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
    transform: translate(0, 0);
  }
  50% {
    transform: translate(30px, -30px);
  }
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: $s-7 $s-8;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  box-shadow: 0 32px 64px rgba(0, 0, 0, 0.3);
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
  margin-bottom: $s-5;

  .brand-mark {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, $brand, darken($brand, 20%));
    border-radius: $r-md;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow: $sh-brand;

    svg {
      width: 22px;
      height: 22px;
    }
  }
  .brand-name {
    font-family: $font-display;
    font-size: 20px;
    font-weight: 700;
    color: $ink;
  }
}

.card-title {
  font-size: 26px;
  font-weight: 800;
  margin: 0 0 $s-1;
  color: $ink;
}
.card-sub {
  font-size: $fs-base;
  color: $ink-2;
  margin: 0 0 $s-5;
}

// 步骤
.steps {
  display: flex;
  margin-bottom: $s-5;
  padding: 0 $s-1;

  .step {
    display: flex;
    flex-direction: column;
    align-items: center;
    flex: 1;
    position: relative;

    .step-ic {
      width: 28px;
      height: 28px;
      border-radius: $r-full;
      background: $surface-alt;
      color: $ink-3;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: $fs-xs;
      font-weight: 700;
      transition: all 0.2s;
    }

    &.done .step-ic {
      background: $success;
      color: #fff;
    }

    .step-label {
      font-size: $fs-xs;
      color: $ink-3;
      margin-top: $s-1;
    }

    &.done .step-label {
      color: $ink;
    }

    .step-line {
      position: absolute;
      top: 14px;
      left: 60%;
      width: 80%;
      height: 2px;
      background: $border;
    }
  }
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: $s-3;

  .field {
    display: flex;
    flex-direction: column;
    gap: $s-1;

    label {
      font-size: $fs-sm;
      font-weight: 600;
      color: $ink;
    }
  }

  .input-wrap {
    display: flex;
    align-items: center;
    gap: $s-2;
    padding: 0 $s-3;
    height: 42px;
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

  .submit {
    height: 46px;
    margin-top: $s-2;
    border: none;
    border-radius: $r-md;
    background: linear-gradient(135deg, $brand, darken($brand, 15%));
    color: #fff;
    font-size: $fs-lg;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: $sh-brand;
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

.card-foot {
  text-align: center;
  margin-top: $s-4;
  font-size: $fs-sm;
  color: $ink-3;

  .link-bold {
    margin-left: 4px;
    color: $brand;
    font-weight: 600;
    text-decoration: none;
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
