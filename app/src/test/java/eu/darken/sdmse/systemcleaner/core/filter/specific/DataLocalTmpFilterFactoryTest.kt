package eu.darken.sdmse.systemcleaner.core.filter.specific

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class DataLocalTmpFilterFactoryTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = DataLocalTmpFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        areaManager = areaManager,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        mockNegative(DataArea.Type.DATA, "local", Flags.DIR)
        mockNegative(DataArea.Type.DATA, "local/tmp", Flags.DIR)
        mockPositive(DataArea.Type.DATA, "local/tmp/$rngString", Flags.DIR)
        mockPositive(DataArea.Type.DATA, "local/tmp/$rngString", Flags.FILE)
        confirm(create())
    }

    @Test fun `only with root`() = runTest {
        DataLocalTmpFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterLocalTmpEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { isRooted() } returns true
            }
        ).isEnabled() shouldBe true

        DataLocalTmpFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterLocalTmpEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                coEvery { isRooted() } returns false
            }
        ).isEnabled() shouldBe false
    }
}