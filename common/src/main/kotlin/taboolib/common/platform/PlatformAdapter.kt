package taboolib.common.platform

/**
 * TabooLib
 * taboolib.common.platform.PlatformAdaptor
 *
 * @author sky
 * @since 2021/6/17 12:04 上午
 */
interface PlatformAdapter {

    fun console(): ProxyCommandSender

    fun onlinePlayers(): List<ProxyPlayer>

    fun adapterPlayer(any: Any): ProxyPlayer

    fun adapterCommandSender(any: Any): ProxyCommandSender

    fun <T> registerListener(event: Class<T>, priority: EventPriority = EventPriority.NORMAL, ignoreCancelled: Boolean = true, func: (T) -> Unit)

    fun callEvent(proxyEvent: ProxyEvent)
}