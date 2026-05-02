package app.trustipay.voice

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmEngineInitializerTest {
    @Test
    fun initialize_gpuSuccess_usesGpuOnly() {
        val factory = FakeEngineFactory(null)
        val initializer = LiteRtLmEngineInitializer(factory)

        val engine = initializer.initialize(ModelPath, CacheDir)

        assertSame(factory.engines[0], engine)
        assertTrue(factory.configs.single().backend is Backend.GPU)
        assertTrue(factory.engines[0].initialized)
        assertFalse(factory.engines[0].closed)
    }

    @Test
    fun initialize_gpuFailure_closesGpuAndFallsBackToCpu() {
        val factory = FakeEngineFactory(IllegalStateException("gpu failed"), null)
        val initializer = LiteRtLmEngineInitializer(factory)

        val engine = initializer.initialize(ModelPath, CacheDir)

        assertSame(factory.engines[1], engine)
        assertTrue(factory.configs[0].backend is Backend.GPU)
        assertTrue(factory.configs[1].backend is Backend.CPU)
        assertTrue(factory.engines[0].closed)
        assertFalse(factory.engines[1].closed)
    }

    @Test
    fun initialize_gpuAndCpuFailure_closesBothAndThrows() {
        val factory = FakeEngineFactory(
            IllegalStateException("gpu failed"),
            IllegalStateException("cpu failed"),
        )
        val initializer = LiteRtLmEngineInitializer(factory)

        val throwable = assertThrows(IllegalStateException::class.java) {
            initializer.initialize(ModelPath, CacheDir)
        }

        assertTrue(throwable.message.orEmpty().contains("GPU and CPU"))
        assertEquals(2, factory.engines.size)
        assertTrue(factory.engines[0].closed)
        assertTrue(factory.engines[1].closed)
    }

    private class FakeEngineFactory(
        private vararg val initializeFailures: Throwable?,
    ) : LiteRtLmEngineFactory {
        val configs = mutableListOf<EngineConfig>()
        val engines = mutableListOf<FakeEngine>()

        override fun create(config: EngineConfig): LiteRtLmEngineHandle {
            val engine = FakeEngine(initializeFailures.getOrNull(configs.size))
            configs += config
            engines += engine
            return engine
        }
    }

    private class FakeEngine(
        private val initializeFailure: Throwable?,
    ) : LiteRtLmEngineHandle {
        var initialized = false
        var closed = false

        override fun initialize() {
            initialized = true
            initializeFailure?.let { throw it }
        }

        override fun createConversation(config: ConversationConfig): LiteRtLmConversationHandle =
            error("Not needed for initializer tests.")

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val ModelPath = "/tmp/model.litertlm"
        const val CacheDir = "/tmp/litertlm-cache"
    }
}
