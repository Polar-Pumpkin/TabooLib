package taboolib.common.platform

interface PlatformExecutor {

    fun execute(async: Boolean = false, delay: Long = 0, period: Long = 0, executor: PlatformTask.() -> Unit): PlatformTask

    interface PlatformTask {

        fun cancel()
    }
}