import path from 'node:path';
import { defineConfig, loadEnv, searchForWorkspaceRoot } from 'vite';
import createPlugins from './vite/plugins';
import autoprefixer from 'autoprefixer'; // css自动添加兼容性前缀

/**
 * 意图面板 SDK 的唯一真源目录。
 *
 * `intent-ui-sdk` 以 npm `link:` 指向后端模块里的静态资源目录 —— 同一份文件
 * 同时是演示页、script 交付形态和 npm 包，改 SDK 立刻在前端 HMR 生效，
 * 不会出现"两份实现漂移"。该目录在 Vite 的工作区根之外，因此必须显式放行。
 */
const intentUiSdkDir = path.resolve(
  import.meta.dirname,
  '../backend/ruoyi-common/ruoyi-common-intent/src/main/resources/intent-ui'
);

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: env.VITE_APP_CONTEXT_PATH,
    resolve: {
      tsconfigPaths: true,
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // https://cn.vitejs.dev/config/#resolve-extensions
    plugins: createPlugins(env, command === 'build'),
    build: {
      chunkSizeWarningLimit: 1500,
      rolldownOptions: {
        checks: {
          invalidAnnotation: false,
          pluginTimings: false
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_APP_PORT),
      open: true,
      fs: {
        // 显式设置 fs.allow 会覆盖 Vite 的默认值（工作区根），
        // 因此既要写回工作区根，也要放行 SDK 真源目录
        allow: [searchForWorkspaceRoot(process.cwd()), intentUiSdkDir]
      },
      proxy: {
        [env.VITE_APP_BASE_API]: {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
          rewrite: path => path.replace(new RegExp('^' + env.VITE_APP_BASE_API), '')
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // additionalData: '@use "@/assets/styles/variables.module.scss as *";'
          // javascriptEnabled: true
        }
      },
      postcss: {
        plugins: [
          // 浏览器兼容性
          autoprefixer(),
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: atRule => {
                atRule.remove();
              }
            }
          }
        ]
      }
    }
  };
});
