package net.thunderbird.legacy.logging

import org.slf4j.LoggerFactory

/**
 * Ripple: stand-in for Thunderbird's legacy logging facade, backed by SLF4J
 * (logback-android in the app).
 */
object Log {
    private val logger = LoggerFactory.getLogger("RippleEngine")

    fun verbose(tag: String? = null, message: () -> String) {
        if (logger.isTraceEnabled) logger.trace(if (tag == null) message() else "[$tag] ${message()}")
    }

    @JvmStatic
    fun v(message: String?, vararg args: Any?) {
        if (logger.isTraceEnabled) logger.trace(formatMessage(message, args))
    }

    @JvmStatic
    fun v(t: Throwable?, message: String?, vararg args: Any?) {
        if (logger.isTraceEnabled) logger.trace(formatMessage(message, args), t)
    }

    @JvmStatic
    fun d(message: String?, vararg args: Any?) {
        if (logger.isDebugEnabled) logger.debug(formatMessage(message, args))
    }

    @JvmStatic
    fun d(t: Throwable?, message: String?, vararg args: Any?) {
        if (logger.isDebugEnabled) logger.debug(formatMessage(message, args), t)
    }

    @JvmStatic
    fun i(message: String?, vararg args: Any?) {
        logger.info(formatMessage(message, args))
    }

    @JvmStatic
    fun i(t: Throwable?, message: String?, vararg args: Any?) {
        logger.info(formatMessage(message, args), t)
    }

    @JvmStatic
    fun w(message: String?, vararg args: Any?) {
        logger.warn(formatMessage(message, args))
    }

    @JvmStatic
    fun w(t: Throwable?, message: String?, vararg args: Any?) {
        logger.warn(formatMessage(message, args), t)
    }

    @JvmStatic
    fun e(message: String?, vararg args: Any?) {
        logger.error(formatMessage(message, args))
    }

    @JvmStatic
    fun e(t: Throwable?, message: String?, vararg args: Any?) {
        logger.error(formatMessage(message, args), t)
    }

    @Suppress("SpreadOperator", "TooGenericExceptionCaught")
    private fun formatMessage(message: String?, args: Array<out Any?>): String {
        return if (message == null) {
            ""
        } else if (args.isEmpty()) {
            message
        } else {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                "$message (Error formatting message: $e, args: ${args.joinToString()})"
            }
        }
    }
}
