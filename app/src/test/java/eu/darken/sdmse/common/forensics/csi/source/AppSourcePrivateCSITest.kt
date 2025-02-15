package eu.darken.sdmse.common.forensics.csi.source

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.source.tools.*
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppSourcePrivateCSITest : BaseCSITest() {

    private val appSourcesArea = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP_PRIVATE
        every { path } returns LocalPath.build("/data/app-private")
    }

    private val bases = setOf(
        appSourcesArea.path,
    )

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    appSourcesArea,
                )
            )
        )
    }

    private fun getProcessor() = AppSourcePrivateCSI(
        areaManager = areaManager,
        similarityFilter = SimilarityFilter(pkgRepo),
        sourceChecks = setOf(
            ApkDirCheck(pkgOps),
            AppSourceClutterCheck(clutterRepo),
            DirectApkCheck(pkgOps, pkgRepo),
            DirToPkgCheck(pkgRepo),
            FileToPkgCheck(pkgRepo),
            LuckyPatcherCheck(pkgRepo),
            SubDirToPkgCheck(gatewaySwitch),
        )
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.APP_APP_PRIVATE)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_APP_PRIVATE
                prefix shouldBe base
                prefixFreePath shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/app", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/data", rngString)) shouldBe null
    }

    @Test fun testProcess_hit() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-1.apk"),
                it.child("eu.thedarken.sdm.test-12.apk"),
                it.child("eu.thedarken.sdm.test-123.apk"),
                it.child("eu.thedarken.sdm.test-1"),
                it.child("eu.thedarken.sdm.test-12"),
                it.child("eu.thedarken.sdm.test-123"),
                it.child("eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/base.apk"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_child() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-123/abc"),
                it.child("eu.thedarken.sdm.test-123/abc/def"),
                it.child("eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/abc/def"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_archiveinfo() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("test.apk"),
            )
        }.flatten()

        val apkArchive = mockk<ApkInfo>().apply {
            every { id } returns packageName
        }
        for (toHit in targets) {
            coEvery { pkgOps.viewArchive(toHit, 0) } returns apkArchive

            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_nested_archiveinfo() = runTest {
        val processor = getProcessor()

        val packageName = "some.pkg".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("ApiDemos"),
            )
        }.flatten()

        val validApkNames = listOf(
            "ApiDemos.apk",
            "base.apk"
        )

        for (base in targets) {
            for (apkName in validApkNames) {
                val target = base.child(apkName)
                val apkArchive = mockk<ApkInfo>().apply {
                    every { id } returns packageName
                }
                coEvery { pkgOps.viewArchive(target, 0) } returns apkArchive

                val locationInfo = processor.identifyArea(target)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testProcess_clutter_hit() = runTest {
        val processor = getProcessor()

        val packageName = "some.pkg".toPkgId()

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.APP_APP_PRIVATE, prefixFree)

        for (base in bases) {

            val locationInfo = processor.identifyArea(base.child(prefixFree))!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_nothing() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val suffix = rngString
            val toHit = base.child(suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
                prefixFreePath shouldBe listOf(suffix)
            }

            processor.findOwners(locationInfo).apply {
                owners.size shouldBe 0
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun testStrictMatching_no_false_positive_dir() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName, LocalPath.build("/data/app-private", "eu.thedarken.sdm.test-2/base.apk"))

        for (base in bases) {
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testStrictMatching_no_false_positive_file() = runTest {
        val processor = getProcessor()
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName, LocalPath.build("/data/app-private", "eu.thedarken.sdm.test-2.apk"))

        for (base in bases) {
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }
}