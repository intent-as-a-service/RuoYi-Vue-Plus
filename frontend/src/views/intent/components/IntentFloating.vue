<!--
  意图浮标（薄适配器）。

  悬浮球与抽屉由 intent-ui-sdk 挂到 body 上（不进入本组件 DOM），因此这里只留一个不可见锚点。

  注意：模板必须只有一个根节点 —— 宿主 layout 外层包了 <transition>，
  check-transition 插件会把「注释 + 元素」判定为多根节点并直接报错，
  所以说明文字只能写在这里或 <script> 里，不能放在 <template> 内部。
-->
<template>
  <span v-show="false" class="intent-floating-anchor" />
</template>

<script setup lang="ts" name="IntentFloating">
import { useRoute } from 'vue-router';
import { currentIntentPage, resolveIntentPageContext } from '../pageContext';
import { setupIntentSdk } from '../sdk';

/** 悬浮球实例（SDK 返回值） */
interface FloatingHandle {
  close: () => void;
  reset: () => void;
  isOpen: () => boolean;
  destroy: () => void;
}

let floating: FloatingHandle | null = null;

const route = useRoute();

/**
 * 页面标识：直接用路由路径（去掉前导斜杠），与后端 IntentSpec.pages 的声明口径一致。
 */
const getPage = () => currentIntentPage(route.path);

/**
 * 当前页面能提供什么上下文：由业务页面通过 registerIntentPageContext 声明。
 */
const getContext = () => resolveIntentPageContext(getPage());

onMounted(() => {
  const intentUI = setupIntentSdk();
  floating = intentUI.mountFloating({ getPage, getContext }) as unknown as FloatingHandle;
});

/**
 * 切换菜单（路由变化）= 换了业务上下文，必须把意图面板还原干净。
 *
 * 为什么宿主这里还要再做一遍：悬浮球是**全站常驻**的（挂在 layout 上），
 * 切菜单不会重新挂载，面板 state 会跨页面一直存活。SDK 只在"重新打开抽屉"时
 * 检测页面变化并自动重置，覆盖不了「抽屉开着的时候路由被改掉」这条路径
 * （浏览器前进后退、代码里 push、redirect 都会走到）。
 */
watch(
  () => currentIntentPage(route.path),
  (page, previous) => {
    if (!floating || page === previous) {
      return;
    }
    if (floating.isOpen()) {
      floating.close();
    }
    floating.reset();
  }
);

onBeforeUnmount(() => {
  floating?.destroy();
  floating = null;
});
</script>
