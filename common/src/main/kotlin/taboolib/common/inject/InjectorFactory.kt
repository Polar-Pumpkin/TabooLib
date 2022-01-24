package taboolib.common.inject

import taboolib.common.LifeCycle
import taboolib.common.TabooLibCommon

object InjectorFactory {

    private lateinit var handler: Handler

    /**
     * 注册 Handler 实现
     */
    fun registerHandler(impl: Handler) {
        handler = impl
    }

    /**
     * 注册 Injector 实现
     */
    fun registerInjector(injector: Injector) {
        handler.register(injector)
    }

    /**
     * 向所有类注入给定生命周期阶段
     */
    fun injectAll(lifeCycle: LifeCycle) {
        if (TabooLibCommon.isRunning()) {
            handler.inject(lifeCycle)
        }
    }

    /**
     * 向特定类注入给定生命周期阶段
     */
    fun injectByLifeCycle(target: Class<*>, lifeCycle: LifeCycle) {
        if (TabooLibCommon.isRunning()) {
            handler.inject(target, lifeCycle)
        }
    }

    /**
     * 不检测生命周期向特定类注入
     */
    fun injectIgnoreLifeCycle(target: Class<*>) {
        if (TabooLibCommon.isRunning()) {
            handler.inject(target)
        }
    }

    abstract class Handler {

        abstract fun register(injector: Injector)

        abstract fun inject(lifeCycle: LifeCycle)

        abstract fun inject(target: Class<*>)

        abstract fun inject(target: Class<*>, lifeCycle: LifeCycle)
    }
}