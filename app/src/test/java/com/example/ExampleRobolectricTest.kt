package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.ReadingMode
import com.example.data.RecentPdfEntity
import com.example.data.SettingsManager
import com.example.ui.common.formatFileSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsManager: SettingsManager

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsManager = SettingsManager(context)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF ALL IN ONE Pro+Ai", appName)
    }

    @Test
    fun `insert and retrieve recent pdf`() = runBlocking {
        val dao = db.recentPdfDao()
        val entity = RecentPdfEntity(
            uri = "content://com.android.providers.media/documents/document/123",
            fileName = "ProjectReport.pdf",
            fileSizeBytes = 2048576L,
            lastOpenedTimestamp = System.currentTimeMillis(),
            pageCount = 42,
            lastPage = 5,
            isFavorite = true
        )
        dao.insertOrUpdate(entity)

        val retrieved = dao.getPdfByUri(entity.uri)
        assertNotNull(retrieved)
        assertEquals("ProjectReport.pdf", retrieved?.fileName)
        assertEquals(42, retrieved?.pageCount)
        assertEquals(true, retrieved?.isFavorite)

        val recents = dao.getAllRecents().first()
        assertEquals(1, recents.size)
    }

    @Test
    fun `insert and retrieve bookmarks`() = runBlocking {
        val dao = db.bookmarkDao()
        val pdfUri = "content://com.example/document.pdf"
        val bookmark = BookmarkEntity(
            pdfUri = pdfUri,
            pageNumber = 7,
            note = "Important financial summary"
        )
        val id = dao.insertBookmark(bookmark)

        val bookmarks = dao.getBookmarksForPdf(pdfUri).first()
        assertEquals(1, bookmarks.size)
        assertEquals(7, bookmarks[0].pageNumber)
        assertEquals("Important financial summary", bookmarks[0].note)

        dao.deleteBookmarkById(id)
        val emptyBookmarks = dao.getBookmarksForPdf(pdfUri).first()
        assertEquals(0, emptyBookmarks.size)
    }

    @Test
    fun `settings manager persists preferences`() {
        settingsManager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, settingsManager.settings.value.themeMode)

        settingsManager.setReadingMode(ReadingMode.TWO_PAGE_SPREAD)
        assertEquals(ReadingMode.TWO_PAGE_SPREAD, settingsManager.settings.value.defaultReadingMode)

        settingsManager.setKeepScreenOn(true)
        assertEquals(true, settingsManager.settings.value.keepScreenOn)
    }

    @Test
    fun `formatFileSize displays readable output`() {
        assertEquals("500 B", formatFileSize(500L))
        assertEquals("500 KB", formatFileSize(500L * 1024))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `pdf annotation models and normalized rect check`() {
        val rect = com.example.pdf.NormalizedRect(0.1f, 0.2f, 0.5f, 0.6f)
        assertEquals(0.4f, rect.width, 0.001f)
        assertEquals(0.4f, rect.height, 0.001f)
        assertEquals(true, rect.contains(0.3f, 0.4f))
        assertEquals(false, rect.contains(0.05f, 0.4f))

        val note = com.example.pdf.PdfAnnotation.StickyNote(
            pageIndex = 0,
            position = com.example.pdf.NormalizedPoint(0.3f, 0.4f),
            noteText = "Important revision"
        )
        assertEquals("Important revision", note.noteText)
        assertEquals(com.example.pdf.AnnotationType.STICKY_NOTE, note.type)
    }

    @Test
    fun `page organizer and merge models state check`() {
        val page1 = com.example.pdf.OrganizedPage(
            sourcePageIndex = 0,
            rotation = 90,
            isBlank = false
        )
        assertEquals(0, page1.sourcePageIndex)
        assertEquals(90, page1.rotation)
        assertEquals(false, page1.isBlank)

        val rotated = page1.copy(rotation = (page1.rotation + 90) % 360)
        assertEquals(180, rotated.rotation)

        val blankPage = com.example.pdf.OrganizedPage(isBlank = true)
        assertEquals(true, blankPage.isBlank)

        val mergeItem = com.example.pdf.MergeItem(
            uri = android.net.Uri.parse("content://media/pdf/1"),
            fileName = "Chapter1.pdf",
            isImage = false,
            pageRangeText = "1-3, 5"
        )
        assertEquals("Chapter1.pdf", mergeItem.fileName)
        assertEquals("1-3, 5", mergeItem.pageRangeText)

        val splitRanges = com.example.pdf.SplitMode.ByRanges("1-3, 5, 7-9")
        assertEquals("1-3, 5, 7-9", splitRanges.rangeExpression)
    }

    @Test
    fun `form field models and normalized rect check`() {
        val rect = com.example.pdf.NormalizedRect(0.1f, 0.2f, 0.5f, 0.35f)
        assertEquals(0.4f, rect.width, 0.001f)
        assertEquals(0.15f, rect.height, 0.001f)

        val field = com.example.pdf.FormFieldItem(
            id = "f_1",
            name = "full_name",
            fullyQualifiedName = "applicant.full_name",
            pageIndex = 0,
            bounds = rect,
            type = com.example.pdf.FormFieldType.TEXT,
            currentValue = "Alice Smith",
            isRequired = true
        )
        assertEquals("applicant.full_name", field.fullyQualifiedName)
        assertEquals("Alice Smith", field.currentValue)
        assertEquals(true, field.isRequired)
        assertEquals(com.example.pdf.FormFieldType.TEXT, field.type)

        val stamp = com.example.pdf.PlacedStampItem(
            id = "s_1",
            type = com.example.pdf.PlacedStampType.CHECKMARK,
            pageIndex = 1,
            normX = 0.5f,
            normY = 0.6f,
            normWidth = 0.1f,
            normHeight = 0.08f
        )
        assertEquals(com.example.pdf.PlacedStampType.CHECKMARK, stamp.type)
        assertEquals(1, stamp.pageIndex)
    }

    @Test
    fun `signature manager storage and background removal`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sigManager = com.example.pdf.SignatureManager(context)

        // 1. Create and save a signature bitmap
        val testBmp = android.graphics.Bitmap.createBitmap(100, 50, android.graphics.Bitmap.Config.ARGB_8888)
        testBmp.eraseColor(android.graphics.Color.BLACK)

        val saved = sigManager.saveSignature(0, testBmp, isInitials = false)
        assertEquals(true, saved)

        val sigs = sigManager.getSavedSignatures()
        assertEquals(1, sigs.size)
        assertEquals(0, sigs[0].slotIndex)
        assertEquals(false, sigs[0].isInitials)

        // 2. Test white background removal
        val whitePaperBmp = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        whitePaperBmp.eraseColor(android.graphics.Color.WHITE)
        // Draw one black pixel
        whitePaperBmp.setPixel(5, 5, android.graphics.Color.BLACK)

        val transparentBmp = sigManager.removeWhiteBackground(whitePaperBmp, threshold = 220)
        assertEquals(android.graphics.Color.TRANSPARENT, transparentBmp.getPixel(0, 0))
        assertEquals(android.graphics.Color.BLACK, transparentBmp.getPixel(5, 5))

        // 3. Test typed cursive signature generator
        val cursiveBmp = sigManager.createTypeSignatureBitmap("Jane Doe", styleIndex = 0)
        assertNotNull(cursiveBmp)
        assertEquals(true, cursiveBmp.width > 0)
        assertEquals(true, cursiveBmp.height > 0)

        // 4. Test delete
        sigManager.deleteSignature(0, isInitials = false)
        val sigsAfterDelete = sigManager.getSavedSignatures()
        assertEquals(0, sigsAfterDelete.size)
    }

    @Test
    fun `autofill snippets persistence check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sm = com.example.data.SettingsManager(context)

        sm.updateProfileSnippets(
            fullName = "Sarah Connor",
            initials = "SC",
            email = "sarah@cyberdyne.org",
            phone = "+1 (800) 555-0199",
            address = "420 Skynet Boulevard, Los Angeles, CA"
        )

        val updated = sm.settings.value
        assertEquals("Sarah Connor", updated.userFullName)
        assertEquals("SC", updated.userInitials)
        assertEquals("sarah@cyberdyne.org", updated.userEmail)
        assertEquals("+1 (800) 555-0199", updated.userPhone)
        assertEquals("420 Skynet Boulevard, Los Angeles, CA", updated.userAddress)
    }

    @Test
    fun `app lock manager PIN verification and hashing test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = com.example.security.AppLockManager(context)

        // Clear any previous state
        lockManager.clearPin()
        assertEquals(false, lockManager.isLockConfigured())
        assertEquals(false, lockManager.isAppLockEnabled())

        // Save new 4-digit PIN
        lockManager.saveNewPin("5829")
        assertEquals(true, lockManager.isLockConfigured())
        assertEquals(true, lockManager.isAppLockEnabled())

        // Test incorrect PIN
        val wrongResult = lockManager.verifyPin("0000")
        assertEquals(false, wrongResult)

        // Test correct PIN
        val correctResult = lockManager.verifyPin("5829")
        assertEquals(true, correctResult)
        assertEquals(false, lockManager.isAppLocked.value)

        // Test timeout settings
        lockManager.setTimeoutSeconds(300L)
        assertEquals(300L, lockManager.getTimeoutSeconds())

        // Test biometric setting
        lockManager.setBiometricsEnabled(true)
        assertEquals(true, lockManager.isBiometricsEnabled())

        // Test clear
        lockManager.clearPin()
        assertEquals(false, lockManager.isLockConfigured())
    }

    @Test
    fun `biometric document protection and encrypted password vault test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockManager = com.example.security.AppLockManager(context)

        val docUri1 = "content://com.example.provider/documents/confidential_contract.pdf"
        val docUri2 = "content://com.example.provider/documents/financial_report.pdf"

        // 1. Initially neither document is biometric-protected
        assertEquals(false, lockManager.isDocumentBiometricProtected(docUri1))
        assertEquals(false, lockManager.isDocumentBiometricProtected(docUri2))

        // 2. Protect docUri1
        val toggleResult1 = lockManager.toggleDocumentBiometricProtection(docUri1)
        assertEquals(true, toggleResult1)
        assertEquals(true, lockManager.isDocumentBiometricProtected(docUri1))
        assertEquals(false, lockManager.isDocumentBiometricProtected(docUri2))
        assertEquals(true, lockManager.protectedDocuments.value.contains(docUri1))

        // 3. Toggle docUri1 off
        val toggleResult2 = lockManager.toggleDocumentBiometricProtection(docUri1)
        assertEquals(false, toggleResult2)
        assertEquals(false, lockManager.isDocumentBiometricProtected(docUri1))
        assertEquals(false, lockManager.protectedDocuments.value.contains(docUri1))

        // 4. Biometric password vault for encrypted PDFs
        assertEquals(false, lockManager.hasSavedDocumentPasswordForBiometrics(docUri2))
        lockManager.saveDocumentPasswordForBiometrics(docUri2, "SecretPdfPass123!")
        assertEquals(true, lockManager.hasSavedDocumentPasswordForBiometrics(docUri2))
        assertEquals("SecretPdfPass123!", lockManager.getSavedDocumentPasswordForBiometrics(docUri2))

        // 5. Remove saved password from biometric vault
        lockManager.removeDocumentPasswordForBiometrics(docUri2)
        assertEquals(false, lockManager.hasSavedDocumentPasswordForBiometrics(docUri2))
        assertEquals(null, lockManager.getSavedDocumentPasswordForBiometrics(docUri2))

        // 6. Test Biometric hardware status method
        val status = lockManager.getBiometricStatus()
        assertNotNull(status)
        val statusDesc = lockManager.getBiometricStatusDescription()
        assertNotNull(statusDesc)
        assertTrue(statusDesc.isNotBlank())
    }

    @Test
    fun `pdf optimization engine metadata, encryption, and sanitization test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val engine = com.example.pdf.PdfOptimizationEngine(context)

        // 1. Create a clean test PDF
        val testPdf = java.io.File(context.cacheDir, "unit_test_doc.pdf")
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        val info = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
        info.title = "Original Confidential Report"
        info.author = "Dr. Bruce Banner"
        info.subject = "Gamma Radiation Studies"
        info.keywords = "gamma, physics, research"
        doc.documentInformation = info
        doc.save(testPdf)
        doc.close()

        // 2. Test reading metadata
        val readMeta = engine.getMetadata(testPdf, null)
        assertEquals("Original Confidential Report", readMeta.title)
        assertEquals("Dr. Bruce Banner", readMeta.author)
        assertEquals("Gamma Radiation Studies", readMeta.subject)
        assertEquals(1, readMeta.pageCount)

        // 3. Test encrypting PDF
        val protectedFile = engine.encryptPdf(
            pdfFile = testPdf,
            uri = null,
            userPassword = "SecretPassword123",
            ownerPassword = "AdminPassword456",
            canPrint = true,
            canCopy = false,
            canEdit = false,
            onProgress = { _, _ -> }
        )
        assertNotNull(protectedFile)
        assertEquals(true, protectedFile.exists())
        assertEquals(true, protectedFile.length() > 0)

        // 3b. Test opening password-protected PDF in viewer with removePasswordPermanently = false
        val docManager = com.example.pdf.PdfDocumentManager(context)
        val viewerUnlockResult = docManager.openPdfWithPassword(
            uri = android.net.Uri.fromFile(protectedFile),
            password = "SecretPassword123",
            removePasswordPermanently = false
        )
        assertEquals(true, viewerUnlockResult is com.example.pdf.PdfLoadResult.Success)
        assertEquals(1, (viewerUnlockResult as com.example.pdf.PdfLoadResult.Success).pageCount)
        docManager.close()

        // 4. Test unlocking PDF permanently via PdfOptimizationEngine
        val unlockedFile = engine.unlockPdf(
            pdfFile = protectedFile,
            uri = null,
            password = "SecretPassword123",
            removePasswordPermanently = true,
            onProgress = { _, _ -> }
        )
        assertNotNull(unlockedFile)
        assertEquals(true, unlockedFile.exists())

        // 5. Test sanitizing PDF
        val sanitizedFile = engine.sanitizePdf(
            pdfFile = testPdf,
            uri = null,
            removeMetadata = true,
            removeAttachments = true,
            removeHiddenXmp = true,
            removeAnnotations = true,
            onProgress = { _, _ -> }
        )
        assertNotNull(sanitizedFile)
        val sanitizedMeta = engine.getMetadata(sanitizedFile, null)
        assertEquals("", sanitizedMeta.title)
        assertEquals("", sanitizedMeta.author)

        // Cleanup
        testPdf.delete()
        protectedFile.delete()
        unlockedFile.delete()
        sanitizedFile.delete()
    }

    @Test
    fun `scan image processor, special modes, scans folder, and searchable pdf ocr layer test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val workDir = java.io.File(context.cacheDir, "scan_unit_test").apply { mkdirs() }
        val ocrEngine = com.example.scan.ScanOcrEngine(context)

        // 1. Create a synthetic document bitmap and save as raw file
        val rawBmp = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(rawBmp)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        val docPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(60f, 80f, 540f, 720f, docPaint)
        val rawFile = java.io.File(workDir, "raw_sample.jpg")
        java.io.FileOutputStream(rawFile).use { out ->
            rawBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }

        // 2. Test automatic corner detection and perspective + filter processing
        val detectedCorners = com.example.scan.ScanImageProcessor.detectDocumentCorners(rawBmp)
        assertEquals(4, detectedCorners.size)

        val processedFile = java.io.File(workDir, "processed_sample.jpg")
        val processSuccess = com.example.scan.ScanImageProcessor.processPageImage(
            rawFile = rawFile,
            corners = detectedCorners,
            rotationDegrees = 90,
            filter = com.example.scan.ScanFilter.MAGIC_COLOR,
            outputFile = processedFile
        )
        assertEquals(true, processSuccess)
        assertEquals(true, processedFile.exists() && processedFile.length() > 0L)

        // 3. Test Book Mode (split 2 facing pages)
        val splitPair = com.example.scan.ScanImageProcessor.splitBookSpread(rawBmp, workDir)
        rawBmp.recycle()
        assertEquals(true, splitPair.first.exists() && splitPair.second.exists())

        // 4. Test ID Card Mode (front and back on one A4 page)
        val idCardCombined = com.example.scan.ScanImageProcessor.combineIdCardFrontAndBack(
            frontFile = splitPair.first,
            backFile = splitPair.second,
            cacheDir = workDir
        )
        assertEquals(true, idCardCombined.exists() && idCardCombined.length() > 0L)

        // 5. Test Searchable PDF export with pre-populated OCR word boxes & Scans folder management
        val pageItem = com.example.scan.ScannedPageItem(
            rawFile = rawFile,
            processedFile = processedFile,
            corners = detectedCorners,
            filter = com.example.scan.ScanFilter.AUTO_ENHANCE,
            recognizedText = "INVOICE TOTAL PAID",
            ocrWords = listOf(
                com.example.scan.OcrWordBox("INVOICE", 0.1f, 0.1f, 0.35f, 0.16f),
                com.example.scan.OcrWordBox("TOTAL", 0.4f, 0.1f, 0.6f, 0.16f),
                com.example.scan.OcrWordBox("PAID", 0.65f, 0.1f, 0.85f, 0.16f)
            )
        )

        val outScanPdf = ocrEngine.exportScanToPdf(
            pages = listOf(pageItem),
            documentTitle = "Invoice_Scan_Test",
            pageSize = com.example.scan.ScanExportPageSize.A4,
            jpegQuality = 0.85f,
            enableSearchableOcr = true,
            captureMode = com.example.scan.ScanCaptureMode.SINGLE_OR_BATCH,
            onProgress = { _, _ -> }
        )
        assertEquals(true, outScanPdf.exists())

        // Verify the invisible OCR text layer is genuinely searchable via PDFTextStripper
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(outScanPdf).use { loadedDoc ->
            assertEquals(1, loadedDoc.numberOfPages)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val extracted = stripper.getText(loadedDoc)
            assertEquals(true, extracted.contains("INVOICE"))
            assertEquals(true, extracted.contains("PAID"))
        }

        // 6. Verify Scans folder list, rename, and delete
        val listBefore = ocrEngine.listSavedScans()
        val targetRecord = listBefore.firstOrNull { it.file.name == "Invoice_Scan_Test.pdf" }
        assertNotNull(targetRecord)

        val renamedFile = ocrEngine.renameSavedScan(targetRecord!!.file, "Renamed_Invoice_Scan")
        assertNotNull(renamedFile)
        assertEquals("Renamed_Invoice_Scan.pdf", renamedFile!!.name)

        val deleted = ocrEngine.deleteSavedScan(renamedFile)
        assertEquals(true, deleted)
        workDir.deleteRecursively()
    }

    @Test
    fun `content editing engine text box, bengali conjuncts, text replacement, image, link, and permanent redaction test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val contentEngine = com.example.pdf.PdfContentEditEngine(context)

        // 1. Create a 2-page test PDF with existing text on both pages
        val srcPdf = java.io.File(context.cacheDir, "content_edit_src.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            // Page 0: Normal page for Add Text (English + Bengali), Replace Text, Image, and Link
            val p0 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(p0)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p0).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(72f, 720f)
                cs.showText("Original Line To Be Replaced")
                cs.endText()
            }

            // Page 1: Secret page for permanent Redaction verification
            val p1 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(p1)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, p1).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("TOP_SECRET_SSN_999_88_7777")
                cs.endText()
            }

            doc.save(srcPdf)
        }

        // 2. Test inspectPage extracts existing text line and bounding box
        val inspectionP0 = contentEngine.inspectPage(srcPdf, null, 0)
        assertEquals(true, inspectionP0.lines.isNotEmpty())
        val firstLine = inspectionP0.lines.first()
        assertEquals(true, firstLine.text.contains("Original Line"))

        // 3. Prepare a sample image file for ImageObject placement & crop/rotation
        val sampleImgFile = java.io.File(context.cacheDir, "edit_sample_img.jpg")
        val bmp = android.graphics.Bitmap.createBitmap(120, 80, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLUE)
        java.io.FileOutputStream(sampleImgFile).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        // 4. Verify Bengali script detection and duplicate / zIndex layer ordering
        assertEquals(true, com.example.pdf.BundledEditFont.containsBengaliScript("সোনার বাংলা যুক্তাক্ষর"))
        assertEquals(false, com.example.pdf.BundledEditFont.containsBengaliScript("Plain English Text"))

        val addedEnglish = com.example.pdf.ContentEditElement.AddedText(
            pageIndex = 0,
            bounds = com.example.pdf.NormalizedRect(0.1f, 0.25f, 0.6f, 0.32f),
            text = "Added Noto Sans Heading",
            font = com.example.pdf.BundledEditFont.NOTO_SANS,
            fontSizeSp = 16f,
            isBold = true,
            zIndex = 1
        )
        val duplicatedEnglish = addedEnglish.duplicateOnPage(0).withUpdatedZIndex(5)
        assertEquals(5, duplicatedEnglish.zIndex)
        assertEquals(true, duplicatedEnglish.bounds.left > addedEnglish.bounds.left)

        val addedBengali = com.example.pdf.ContentEditElement.AddedText(
            pageIndex = 0,
            bounds = com.example.pdf.NormalizedRect(0.1f, 0.36f, 0.7f, 0.44f),
            text = "সোনার বাংলা যুক্তাক্ষর পরীক্ষা",
            font = com.example.pdf.BundledEditFont.NOTO_SANS_BENGALI,
            fontSizeSp = 16f,
            zIndex = 2
        )

        val replacedLine = com.example.pdf.ContentEditElement.ReplacedText(
            pageIndex = 0,
            bounds = firstLine.bounds,
            originalText = firstLine.text,
            replacementText = "Updated Replacement Line",
            font = com.example.pdf.BundledEditFont.HELVETICA,
            fontSizeSp = 14f,
            zIndex = 3
        )

        val placedImg = com.example.pdf.ContentEditElement.ImageObject(
            pageIndex = 0,
            bounds = com.example.pdf.NormalizedRect(0.15f, 0.5f, 0.45f, 0.65f),
            imageFile = sampleImgFile,
            rotationDegrees = 90f,
            cropRect = com.example.pdf.NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            zIndex = 4
        )

        val webLink = com.example.pdf.ContentEditElement.LinkArea(
            pageIndex = 0,
            bounds = com.example.pdf.NormalizedRect(0.1f, 0.7f, 0.5f, 0.76f),
            linkType = com.example.pdf.PdfLinkType.WEB_URL,
            targetUrlOrEmail = "https://developer.android.com",
            zIndex = 6
        )

        // Mark Page 1 secret text for permanent redaction
        val inspectionP1 = contentEngine.inspectPage(srcPdf, null, 1)
        val secretLineBounds = inspectionP1.lines.first().bounds
        val redactionMark = com.example.pdf.ContentEditElement.RedactionMark(
            pageIndex = 1,
            bounds = secretLineBounds,
            zIndex = 10
        )

        // 5. Apply all edits & permanent redactions to output PDF
        val outPdf = contentEngine.applyContentEditsToPdf(
            sourceFile = srcPdf,
            sourceUri = null,
            edits = listOf(
                addedEnglish,
                addedBengali,
                replacedLine,
                placedImg,
                webLink,
                redactionMark
            ),
            onProgress = { _, _, _ -> }
        )

        assertNotNull(outPdf)
        assertEquals(true, outPdf!!.exists() && outPdf.length() > 0L)

        // 6. Verify output PDF:
        //    - Page 0 has embedded text & link annotation
        //    - Page 1 has TOP_SECRET_SSN_999_88_7777 permanently stripped!
        val loadedLinks = contentEngine.loadExistingLinks(outPdf, null)
        assertEquals(1, loadedLinks.size)
        assertEquals(com.example.pdf.PdfLinkType.WEB_URL, loadedLinks[0].linkType)
        assertEquals("https://developer.android.com", loadedLinks[0].targetUrlOrEmail)

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(outPdf).use { verifiedDoc ->
            assertEquals(2, verifiedDoc.numberOfPages)

            val stripperP0 = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                startPage = 1
                endPage = 1
            }
            val textP0 = stripperP0.getText(verifiedDoc)
            assertEquals(true, textP0.contains("Added Noto Sans Heading"))
            assertEquals(true, textP0.contains("Updated Replacement Line"))

            val stripperP1 = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                startPage = 2
                endPage = 2
            }
            val textP1 = stripperP1.getText(verifiedDoc)
            // Underlying secret text on Page 1 must be permanently gone!
            assertEquals(false, textP1.contains("TOP_SECRET_SSN_999_88_7777"))
        }

        srcPdf.delete()
        sampleImgFile.delete()
        outPdf.delete()
    }

    @Test
    fun `AI assistant engine chunks long documents, parses page citations, enforces consent and daily limits, and exports side-by-side translation with disclaimer`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // 1. Verify mandatory strings (consent message and AI disclaimer)
        assertEquals(
            "This sends the text of the pages you choose to a cloud AI service.",
            com.example.ai.AI_CONSENT_DIALOG_MESSAGE
        )
        assertEquals(
            "AI generated, may contain mistakes",
            com.example.ai.AI_DISCLAIMER_TEXT
        )

        // 2. Verify SettingsManager AI consent, AI toggle, and daily usage limit counter (default 5/day)
        settingsManager.resetAiDailyUsageCounter()
        settingsManager.setAiConsentGranted(false)
        settingsManager.setAiEnabled(true)
        settingsManager.setAiDailyLimit(5)

        assertEquals(false, settingsManager.settings.value.aiConsentGranted)
        assertEquals(true, settingsManager.settings.value.aiEnabled)
        assertEquals(5, settingsManager.settings.value.aiDailyLimit)
        // Without consent, canMakeAiRequest() must return false
        assertEquals(false, settingsManager.canMakeAiRequest())

        // Grant consent -> now allowed up to daily limit (5)
        settingsManager.setAiConsentGranted(true)
        assertEquals(true, settingsManager.settings.value.aiConsentGranted)

        // Consume 5 requests and verify limit enforcement
        repeat(5) { idx ->
            assertEquals(true, settingsManager.canMakeAiRequest())
            val recorded = settingsManager.recordAiRequestUsed()
            assertEquals(true, recorded)
            assertEquals(idx + 1, settingsManager.settings.value.aiRequestsUsedToday)
        }
        assertEquals(false, settingsManager.canMakeAiRequest())

        // Increase configurable limit to 10 -> can make requests again
        settingsManager.setAiDailyLimit(10)
        assertEquals(true, settingsManager.canMakeAiRequest())

        // Reset counter
        settingsManager.resetAiDailyUsageCounter()
        assertEquals(0, settingsManager.settings.value.aiRequestsUsedToday)

        // 3. Verify document chunking and top-K relevant chunk retrieval for Chat with PDF
        val pages = mapOf(
            0 to "Introduction to corporate governance and board responsibilities in 2026.",
            1 to "Quarterly financial statement: Total revenue reached $42.5 million with 18% operating margin.",
            2 to "বাংলাদেশ ব্যাংকের আর্থিক প্রতিবেদন এবং মুদ্রাস্ফীতি নিয়ন্ত্রণ নীতিমালা।",
            3 to "Appendix A: Legal disclaimers, environmental sustainability metrics, and solar energy usage."
        )
        val chunks = com.example.ai.PdfAiAssistantEngine.splitDocumentTextIntoChunks(
            pagesText = pages,
            maxChunkChars = 120,
            overlapChars = 20
        )
        assertEquals(true, chunks.size >= 4)

        val financialChunks = com.example.ai.PdfAiAssistantEngine.selectRelevantChunks(
            chunks = chunks,
            query = "What was the total revenue and operating margin?",
            maxChunks = 2,
            maxTotalChars = 500
        )
        assertEquals(true, financialChunks.isNotEmpty())
        assertEquals(true, financialChunks.any { it.pageNumber == 2 })

        // 4. Verify page citation parsing for both English and Bengali references
        val citations = com.example.ai.PdfAiAssistantEngine.parsePageCitations(
            text = "Revenue grew to $42.5M [Page 2] while monetary policy is covered on (Page 3) and পৃষ্ঠা ৪."
        )
        assertEquals(listOf(2, 3, 4), citations)

        // 5. Verify side-by-side Translation export to PDF and TXT with AI disclaimer
        val aiEngine = com.example.ai.PdfAiAssistantEngine(context)
        val translationResult = com.example.ai.AiTranslationResult(
            sourceLanguage = "English",
            targetLanguage = com.example.ai.AiOutputLanguage.BENGALI,
            pairs = listOf(
                com.example.ai.TranslationPair(
                    label = "Page 1",
                    pageNumber = 1,
                    originalText = "This contract is governed by applicable law.",
                    translatedText = "এই চুক্তি প্রযোজ্য আইন দ্বারা পরিচালিত হবে।"
                ),
                com.example.ai.TranslationPair(
                    label = "Page 2",
                    pageNumber = 2,
                    originalText = "Both parties agree to the quarterly schedule.",
                    translatedText = "উভয় পক্ষ ত্রৈমাসিক সময়সূচীতে সম্মত হয়েছে।"
                )
            )
        )

        val exportedTxt = java.io.File(context.cacheDir, "test_translation_export.txt")
        val txtSuccess = aiEngine.exportTranslationAsTxt(translationResult, exportedTxt)
        assertEquals(true, txtSuccess)
        val txtContent = exportedTxt.readText()
        assertEquals(true, txtContent.contains(com.example.ai.AI_DISCLAIMER_TEXT))
        assertEquals(true, txtContent.contains("This contract is governed by applicable law."))
        assertEquals(true, txtContent.contains("এই চুক্তি প্রযোজ্য আইন দ্বারা পরিচালিত হবে।"))

        val exportedPdf = java.io.File(context.cacheDir, "test_translation_export.pdf")
        val pdfSuccess = aiEngine.exportTranslationAsPdf(translationResult, exportedPdf)
        assertEquals(true, pdfSuccess)
        assertEquals(true, exportedPdf.exists() && exportedPdf.length() > 0L)

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(exportedPdf).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val extracted = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            assertEquals(true, extracted.contains(com.example.ai.AI_DISCLAIMER_TEXT))
            assertEquals(true, extracted.contains("This contract is governed by applicable"))
            assertEquals(true, extracted.contains("Both parties agree to the quarterly"))
        }

        exportedTxt.delete()
        exportedPdf.delete()
    }

    @Test
    fun `polish localization, bengali typography, onboarding, home folders and sorting, low-memory mode, global error copy details, analytics opt-in, and about licenses test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Localization (English & Bengali values-bn) and In-App Language Switcher
        settingsManager.setAppLanguage(com.example.data.AppLanguage.BENGALI)
        assertEquals(com.example.data.AppLanguage.BENGALI, settingsManager.settings.value.appLanguage)

        val bnContext = SettingsManager.createLocalizedContext(context, com.example.data.AppLanguage.BENGALI)
        val enContext = SettingsManager.createLocalizedContext(context, com.example.data.AppLanguage.ENGLISH)

        assertEquals("হোম", bnContext.getString(R.string.nav_home))
        assertEquals("Home", enContext.getString(R.string.nav_home))
        assertEquals("এড়িয়ে যান", bnContext.getString(R.string.onboarding_skip))
        assertEquals("Skip", enContext.getString(R.string.onboarding_skip))

        settingsManager.setAppLanguage(com.example.data.AppLanguage.SYSTEM)
        assertEquals(com.example.data.AppLanguage.SYSTEM, settingsManager.settings.value.appLanguage)

        // 2. Bengali Typography (Noto Sans Bengali & Noto Sans)
        val bnTypography = com.example.ui.theme.buildAppTypography(useBengaliFont = true)
        val enTypography = com.example.ui.theme.buildAppTypography(useBengaliFont = false)
        assertEquals(com.example.ui.theme.NotoSansBengaliFontFamily, bnTypography.bodyLarge.fontFamily)
        assertEquals(com.example.ui.theme.NotoSansFontFamily, enTypography.bodyLarge.fontFamily)

        // 3. Onboarding (max 3 screens, skippable)
        assertEquals(3, com.example.ui.onboarding.ONBOARDING_MAX_SCREENS)
        settingsManager.setOnboardingCompleted(false)
        assertEquals(false, settingsManager.settings.value.onboardingCompleted)
        settingsManager.setOnboardingCompleted(true)
        assertEquals(true, settingsManager.settings.value.onboardingCompleted)

        // 4. Home Improvements: Folders, Favorites, Sort by Name/Date/Size
        val dao = db.recentPdfDao()
        val repo = com.example.data.PdfRepository(dao, db.bookmarkDao())

        dao.insertOrUpdate(
            RecentPdfEntity(
                uri = "content://test/doc_b.pdf",
                fileName = "B_Invoice.pdf",
                fileSizeBytes = 500_000L,
                lastOpenedTimestamp = 1000L,
                pageCount = 5,
                isFavorite = false,
                folderName = "Finance"
            )
        )
        dao.insertOrUpdate(
            RecentPdfEntity(
                uri = "content://test/doc_a.pdf",
                fileName = "A_Contract.pdf",
                fileSizeBytes = 2_000_000L,
                lastOpenedTimestamp = 2000L,
                pageCount = 12,
                isFavorite = true,
                folderName = "Legal"
            )
        )

        val financePdfs = dao.getByFolder("Finance").first()
        assertEquals(1, financePdfs.size)
        assertEquals("B_Invoice.pdf", financePdfs[0].fileName)

        repo.assignToFolder("content://test/doc_b.pdf", "Legal")
        val legalPdfs = dao.getByFolder("Legal").first()
        assertEquals(2, legalPdfs.size)

        settingsManager.setHomeSortOrder(com.example.data.HomeSortOrder.NAME)
        assertEquals(com.example.data.HomeSortOrder.NAME, settingsManager.settings.value.homeSortOrder)
        settingsManager.setHomeSortOrder(com.example.data.HomeSortOrder.SIZE)
        assertEquals(com.example.data.HomeSortOrder.SIZE, settingsManager.settings.value.homeSortOrder)

        val addedFolder = settingsManager.addCustomFolder("Tax 2026")
        assertEquals(true, addedFolder)
        assertEquals(true, settingsManager.settings.value.customFolders.contains("Tax 2026"))

        // 5. Low-Memory Mode Detection & Smaller Cache / Lower Render Resolution
        val memInfo = SettingsManager.detectDeviceMemoryInfo(context)
        assertEquals(true, memInfo.memoryClassMb > 0)

        val docManager = com.example.pdf.PdfDocumentManager(context)
        docManager.setLowMemoryMode(false)
        val normalCacheKb = docManager.activePageCacheMaxKb
        val normalScale = docManager.activeRenderScaleMultiplier

        docManager.setLowMemoryMode(true)
        val lowMemCacheKb = docManager.activePageCacheMaxKb
        val lowMemScale = docManager.activeRenderScaleMultiplier

        assertEquals(true, lowMemCacheKb <= normalCacheKb)
        assertEquals(true, lowMemScale < normalScale)
        docManager.close()

        // 6. Privacy-First Analytics Opt-In (Strictly disabled unless user opts in)
        settingsManager.setAnalyticsOptIn(false)
        assertEquals(false, settingsManager.settings.value.analyticsOptIn)
        assertEquals(false, settingsManager.trackAnalyticsEvent("open_pdf"))
        assertEquals(0, settingsManager.recordedAnalyticsEvents.value.size)

        settingsManager.setAnalyticsOptIn(true)
        assertEquals(true, settingsManager.trackAnalyticsEvent("open_pdf"))
        assertEquals(1, settingsManager.recordedAnalyticsEvents.value.size)
        settingsManager.setAnalyticsOptIn(false)

        // 7. Global Error Screen & Copy Details
        val report = com.example.ui.common.GlobalErrorManager.formatThrowableReport(
            IllegalArgumentException("Corrupted xref table at byte 4096"),
            "PDF Parser"
        )
        assertEquals(true, report.fullTechnicalDetails.contains("Corrupted xref table at byte 4096"))
        assertEquals(true, report.fullTechnicalDetails.contains(BuildConfig.VERSION_NAME))
        com.example.ui.common.GlobalErrorManager.copyErrorDetailsToClipboard(context, report.fullTechnicalDetails)

        // 8. About Screen: Version, Complete Library Licenses & Privacy Policy Placeholder
        val libraries = com.example.ui.about.AppAboutRepository.getAllBundledLibraries()
        assertEquals(true, libraries.size >= 15)
        assertEquals(true, libraries.any { it.name.contains("PdfBox-Android") })
        assertEquals(true, libraries.any { it.name.contains("Noto Sans & Noto Sans Bengali") })
        assertEquals(true, libraries.any { it.name.contains("Firebase AI Logic") })
        assertEquals(
            "https://pdfallinone.app/privacy-policy",
            com.example.ui.about.AppAboutRepository.PRIVACY_POLICY_PLACEHOLDER_URL
        )
    }

    @Test
    fun `error report log sanitization test`(): Unit = runBlocking {
        // Verify file names and paths are redacted from error reports
        val sanitized = com.example.ui.common.GlobalErrorManager.sanitizeFileNamesAndPaths(
            "Failed to parse /storage/emulated/0/Download/SecretTaxReturn2026.pdf and content://com.android.providers/document/99"
        )
        assertEquals(false, sanitized.contains("SecretTaxReturn2026.pdf"))
        assertEquals(false, sanitized.contains("/storage/emulated/0/Download"))
        assertEquals(true, sanitized.contains("content://[redacted-uri]"))
    }

    @Test
    fun `watermarking with Bengali and positions and print helper preparation test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // Create a 4-page PDF for watermark and print testing
        val sourcePdf = java.io.File(context.cacheDir, "watermark_print_source.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            repeat(4) {
                doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            }
            doc.save(sourcePdf)
        }

        // 1. Test Bengali + positioned watermark on custom page range ("1, 3")
        val optEngine = com.example.pdf.PdfOptimizationEngine(context)
        val watermarkedFile = optEngine.applyWatermarkAndDecorations(
            pdfFile = sourcePdf,
            uri = null,
            watermarkConfig = com.example.pdf.WatermarkConfig(
                isText = true,
                text = "গোপনীয় - CONFIDENTIAL",
                opacity = 0.35f,
                rotationDegrees = 45f,
                fontSize = 48f,
                position = com.example.pdf.WatermarkPosition.TOP_RIGHT,
                pageRange = "1, 3"
            ),
            headerFooterConfig = com.example.pdf.HeaderFooterConfig(
                showPageNumbers = true,
                headerText = "প্রকল্প প্রতিবেদন",
                footerText = "Internal Use Only"
            ),
            onProgress = { _, _ -> }
        )
        assertEquals(true, watermarkedFile.exists() && watermarkedFile.length() > 0L)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(watermarkedFile).use { doc ->
            assertEquals(4, doc.numberOfPages)
        }

        // 2. Test PdfPrintHelper page range resolution & print spool preparation
        val printHelper = com.example.pdf.PdfPrintHelper(context)
        val parsedPages = printHelper.parseCustomPageRange("1-2, 4", totalPages = 4)
        assertEquals(listOf(0, 1, 3), parsedPages)

        val printConfig = com.example.pdf.PrintJobConfig(
            pageSelection = com.example.pdf.PrintPageSelection.CUSTOM_RANGE,
            customRangeText = "1-2, 4",
            paperSize = com.example.pdf.PrintPaperSize.A4,
            orientation = com.example.pdf.PrintOrientation.PORTRAIT,
            isColor = false,
            includePrintWatermark = true,
            printWatermarkText = "COPY",
            includePageNumbers = true
        )
        val preparedPrintFile = printHelper.preparePrintPdfFile(
            sourceFile = sourcePdf,
            sourceUri = null,
            config = printConfig
        )
        assertEquals(true, preparedPrintFile.exists() && preparedPrintFile.length() > 0L)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(preparedPrintFile).use { printedDoc ->
            assertEquals(3, printedDoc.numberOfPages)
        }

        sourcePdf.delete()
        watermarkedFile.delete()
        preparedPrintFile.delete()
    }

    @Test
    fun `page rotation and custom bounding box crop test`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val opEngine = com.example.pdf.PdfOperationEngine(context)

            // Create a 2-page sample PDF
            val samplePdf = File(context.cacheDir, "sample_rot_crop_${System.currentTimeMillis()}.pdf")
            com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
                doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
                doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
                doc.save(samplePdf)
            }

            // Test custom bounding box crop on page 0
            val cropBox = com.example.pdf.NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f)
            val croppedFile = opEngine.cropCustomBoundingBox(
                sourceUri = android.net.Uri.fromFile(samplePdf),
                pageIndex = 0,
                normalizedBox = cropBox,
                applyToAllPages = false
            )
            assertEquals(true, croppedFile.exists())
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(croppedFile).use { cDoc ->
                val page0Crop = cDoc.getPage(0).cropBox
                assertNotNull(page0Crop)
                assertTrue(page0Crop.width < com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width)
            }

            samplePdf.delete()
            croppedFile.delete()
        }
    }

    @Test
    fun `ml kit ocr text models and searchable pdf text layer embedding test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val ocrEngine = com.example.scan.ScanOcrEngine(context)
        val pdfBoxHelper = com.example.pdf.PdfBoxHelper(context)

        // 1. Create a non-searchable scanned-style PDF (page with image/no text)
        val scannedPdf = File(context.cacheDir, "scanned_non_searchable_${System.currentTimeMillis()}.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
            doc.save(scannedPdf)
        }

        // Verify initial PDF has 0 searchable text
        val initialText = pdfBoxHelper.extractPageText(scannedPdf, null, 0)
        assertTrue(initialText.isBlank())

        // Verify page count helper
        val pageCount = ocrEngine.getPdfPageCount(scannedPdf, null)
        assertEquals(1, pageCount)

        // 2. Construct ML Kit OCR result with words, lines, and blocks
        val words = listOf(
            com.example.scan.OcrWordBox("INVOICE", 0.1f, 0.1f, 0.3f, 0.15f),
            com.example.scan.OcrWordBox("#2026-99", 0.32f, 0.1f, 0.52f, 0.15f),
            com.example.scan.OcrWordBox("Total:", 0.1f, 0.25f, 0.25f, 0.3f),
            com.example.scan.OcrWordBox("$1,250.00", 0.28f, 0.25f, 0.48f, 0.3f)
        )
        val line1 = com.example.scan.OcrTextLine(
            text = "INVOICE #2026-99",
            leftNorm = 0.1f,
            topNorm = 0.1f,
            rightNorm = 0.52f,
            bottomNorm = 0.15f
        )
        val line2 = com.example.scan.OcrTextLine(
            text = "Total: $1,250.00",
            leftNorm = 0.1f,
            topNorm = 0.25f,
            rightNorm = 0.48f,
            bottomNorm = 0.3f
        )
        val block = com.example.scan.OcrTextBlock(
            text = "INVOICE #2026-99\nTotal: $1,250.00",
            leftNorm = 0.1f,
            topNorm = 0.1f,
            rightNorm = 0.52f,
            bottomNorm = 0.3f,
            lines = listOf(line1, line2)
        )
        val pageResult = com.example.scan.OcrPageResult(
            fullText = block.text,
            words = words,
            lines = listOf(line1, line2),
            blocks = listOf(block)
        )

        assertEquals(2, pageResult.lines.size)
        assertEquals(1, pageResult.blocks.size)
        assertEquals("INVOICE #2026-99", pageResult.lines[0].text)

        // 3. Embed the recognized OCR text layer into the non-searchable PDF
        val searchablePdf = ocrEngine.embedSearchableOcrIntoPdf(
            sourceFile = scannedPdf,
            sourceUri = null,
            outputFileName = "Test_Invoice_Searchable",
            cachedPageResults = mapOf(0 to pageResult)
        )
        assertTrue(searchablePdf.exists() && searchablePdf.length() > 0L)

        // 4. Verify the output PDF now has selectable/searchable text extractable via PDFBox
        val extractedAfterOcr = pdfBoxHelper.extractPageText(
            searchablePdf,
            null,
            0
        )
        assertTrue(extractedAfterOcr.contains("INVOICE"))
        assertTrue(extractedAfterOcr.contains("2026-99"))
        assertTrue(extractedAfterOcr.contains("1,250.00"))

        scannedPdf.delete()
        searchablePdf.delete()
    }

    @Test
    fun `pdf to ms word docx excel xlsx powerpoint pptx rtf html markdown and txt conversion test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // Create a sample 2-page PDF with headings and tabular lines
        val samplePdf = File(context.cacheDir, "sample_for_office_${System.currentTimeMillis()}.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page1 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page1)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page1).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText("QUARTERLY FINANCIAL REPORT")
                cs.endText()

                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 710f)
                cs.showText("Department    Revenue    Growth")
                cs.endText()

                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 685f)
                cs.showText("Cloud AI      $450,000   +28%")
                cs.endText()
            }

            val page2 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page2)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page2).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText("Executive summary and conclusions for Q4.")
                cs.endText()
            }
            doc.save(samplePdf)
        }

        val converter = com.example.pdf.PdfDocumentConverterEngine(context)

        // 1. Convert to MS Word (.docx)
        val docxFile = converter.convertPdfDocument(
            sourceFile = samplePdf,
            sourceUri = null,
            sourceFileName = "Financial_Report.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.WORD_DOCX,
                autoOcrScannedPages = false,
                detectTablesAndHeadings = true,
                outputBaseName = "Financial_Report_Word"
            )
        )
        assertTrue(docxFile.exists() && docxFile.name.endsWith(".docx"))
        java.util.zip.ZipFile(docxFile).use { zip ->
            val docEntry = zip.getEntry("word/document.xml")
            assertNotNull(docEntry)
            val xml = zip.getInputStream(docEntry).bufferedReader().readText()
            assertTrue(xml.contains("QUARTERLY FINANCIAL REPORT"))
            assertTrue(xml.contains("Cloud AI"))
            assertTrue(xml.contains("<w:tbl>"))
        }

        // 2. Convert to MS Excel (.xlsx)
        val xlsxFile = converter.convertPdfDocument(
            sourceFile = samplePdf,
            sourceUri = null,
            sourceFileName = "Financial_Report.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.EXCEL_XLSX,
                autoOcrScannedPages = false,
                outputBaseName = "Financial_Report_Excel"
            )
        )
        assertTrue(xlsxFile.exists() && xlsxFile.name.endsWith(".xlsx"))
        java.util.zip.ZipFile(xlsxFile).use { zip ->
            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            assertNotNull(sheetEntry)
            val sheetXml = zip.getInputStream(sheetEntry).bufferedReader().readText()
            assertTrue(sheetXml.contains("QUARTERLY FINANCIAL REPORT"))
            assertTrue(sheetXml.contains("Cloud AI"))
        }

        // 3. Convert to MS PowerPoint (.pptx)
        val pptxFile = converter.convertPdfDocument(
            sourceFile = samplePdf,
            sourceUri = null,
            sourceFileName = "Financial_Report.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.POWERPOINT_PPTX,
                autoOcrScannedPages = false,
                outputBaseName = "Financial_Report_Slides"
            )
        )
        assertTrue(pptxFile.exists() && pptxFile.name.endsWith(".pptx"))
        java.util.zip.ZipFile(pptxFile).use { zip ->
            assertNotNull(zip.getEntry("ppt/slides/slide1.xml"))
            assertNotNull(zip.getEntry("ppt/slides/slide2.xml"))
        }

        // 4. Convert to RTF, CSV, HTML, Markdown, and Plain Text
        for (fmt in listOf(
            com.example.pdf.PdfExportFormat.RTF_DOCUMENT,
            com.example.pdf.PdfExportFormat.CSV_SPREADSHEET,
            com.example.pdf.PdfExportFormat.HTML_WEBPAGE,
            com.example.pdf.PdfExportFormat.MARKDOWN_MD,
            com.example.pdf.PdfExportFormat.PLAIN_TEXT_TXT
        )) {
            val exported = converter.convertPdfDocument(
                sourceFile = samplePdf,
                sourceUri = null,
                sourceFileName = "Financial_Report.pdf",
                config = com.example.pdf.PdfConversionConfig(
                    format = fmt,
                    autoOcrScannedPages = false,
                    pageRangeText = "1",
                    outputBaseName = "Report_${fmt.extension}"
                )
            )
            assertTrue(exported.exists() && exported.name.endsWith(".${fmt.extension}"))
            val content = exported.readText()
            assertTrue(content.contains("QUARTERLY FINANCIAL REPORT"))
            exported.delete()
        }

        samplePdf.delete()
        docxFile.delete()
        xlsxFile.delete()
        pptxFile.delete()
    }

    @Test
    fun `universal file viewer parses MS Word docx, MS Excel xlsx, CSV, PowerPoint pptx, RTF, and JPEG image files and exports them to PDF`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // First create a sample PDF and convert it to DOCX, XLSX, PPTX, CSV, and RTF so we have real Office files
        val samplePdf = File(context.cacheDir, "viewer_source_${System.currentTimeMillis()}.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText("PROJECT ROADMAP 2026")
                cs.endText()

                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 710f)
                cs.showText("Quarter    Milestone    Budget")
                cs.endText()

                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 685f)
                cs.showText("Q1 2026    Alpha Launch $120,000")
                cs.endText()
            }
            doc.save(samplePdf)
        }

        val converter = com.example.pdf.PdfDocumentConverterEngine(context)
        val parser = com.example.viewer.UniversalFileParserEngine(context)

        // 1. Test MS Word (.docx) Viewer Parser + Save as PDF
        val docxFile = converter.convertPdfDocument(
            sourceFile = samplePdf,
            sourceUri = null,
            sourceFileName = "Roadmap.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.WORD_DOCX,
                autoOcrScannedPages = false,
                outputBaseName = "Roadmap_Word"
            )
        )
        val parsedWord = parser.parseFile(
            uri = android.net.Uri.fromFile(docxFile),
            directFile = docxFile,
            explicitFileName = docxFile.name
        )
        assertTrue(parsedWord is com.example.viewer.ParsedUniversalFile.WordDocument)
        val wordDoc = parsedWord as com.example.viewer.ParsedUniversalFile.WordDocument
        assertEquals(com.example.viewer.UniversalFileType.WORD, wordDoc.fileType)
        assertTrue(wordDoc.plainText.contains("PROJECT ROADMAP 2026"))
        assertTrue(wordDoc.blocks.any { it is com.example.viewer.WordDocBlock.Table })

        val wordToPdf = parser.exportViewedFileToPdf(wordDoc)
        assertTrue(wordToPdf.exists() && wordToPdf.length() > 0L)

        // 2. Test MS Excel (.xlsx) Workbook Viewer Parser + Save as PDF
        val xlsxFile = converter.convertPdfDocument(
            sourceFile = samplePdf,
            sourceUri = null,
            sourceFileName = "Roadmap.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.EXCEL_XLSX,
                autoOcrScannedPages = false,
                outputBaseName = "Roadmap_Excel"
            )
        )
        val parsedExcel = parser.parseFile(
            uri = android.net.Uri.fromFile(xlsxFile),
            directFile = xlsxFile,
            explicitFileName = xlsxFile.name
        )
        assertTrue(parsedExcel is com.example.viewer.ParsedUniversalFile.ExcelWorkbook)
        val excelBook = parsedExcel as com.example.viewer.ParsedUniversalFile.ExcelWorkbook
        assertEquals(com.example.viewer.UniversalFileType.EXCEL, excelBook.fileType)
        assertTrue(excelBook.sheets.isNotEmpty())
        assertTrue(excelBook.sheets.first().rows.flatten().any { it.contains("PROJECT ROADMAP 2026") })

        val excelToPdf = parser.exportViewedFileToPdf(excelBook)
        assertTrue(excelToPdf.exists() && excelToPdf.length() > 0L)

        // 3. Test CSV Spreadsheet Viewer Parser
        val csvFile = File(context.cacheDir, "sample_sheet.csv")
        csvFile.writeText("Item,Quantity,Price\nLaptop,5,$1200\nMonitor,10,$350")
        val parsedCsv = parser.parseFile(
            uri = android.net.Uri.fromFile(csvFile),
            directFile = csvFile,
            explicitFileName = csvFile.name
        )
        assertTrue(parsedCsv is com.example.viewer.ParsedUniversalFile.ExcelWorkbook)
        val csvBook = parsedCsv as com.example.viewer.ParsedUniversalFile.ExcelWorkbook
        assertEquals(3, csvBook.sheets.first().rows.size)
        assertEquals("Laptop", csvBook.sheets.first().rows[1][0])

        // 4. Test JPEG (.jpg) Image Viewer Parser + Save as PDF
        val jpegFile = File(context.cacheDir, "sample_photo.jpg")
        val bmp = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(30, 136, 229))
        jpegFile.outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        val parsedJpeg = parser.parseFile(
            uri = android.net.Uri.fromFile(jpegFile),
            directFile = jpegFile,
            explicitFileName = jpegFile.name
        )
        assertTrue(parsedJpeg is com.example.viewer.ParsedUniversalFile.ImageDocument)
        val imageDoc = parsedJpeg as com.example.viewer.ParsedUniversalFile.ImageDocument
        assertEquals(com.example.viewer.UniversalFileType.IMAGE, imageDoc.fileType)
        assertEquals(320, imageDoc.widthPx)
        assertEquals(240, imageDoc.heightPx)
        assertEquals("JPEG", imageDoc.formatLabel)

        val jpegToPdf = parser.exportViewedFileToPdf(imageDoc)
        assertTrue(jpegToPdf.exists() && jpegToPdf.length() > 0L)

        samplePdf.delete()
        docxFile.delete()
        xlsxFile.delete()
        csvFile.delete()
        jpegFile.delete()
        wordToPdf.delete()
        excelToPdf.delete()
        jpegToPdf.delete()
    }

    @Test
    fun `drag and drop page organizer reorders rotates and deletes pages in an open pdf document`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // Create a 4-page PDF document with distinct page labels
        val sourcePdf = File(context.cacheDir, "organizer_test_4pages.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            for (i in 1..4) {
                val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 18f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("ORIGINAL_PAGE_$i")
                    cs.endText()
                }
            }
            doc.save(sourcePdf)
        }

        val sourceUri = android.net.Uri.fromFile(sourcePdf)
        val initialPages = (0 until 4).map { idx ->
            com.example.pdf.OrganizedPage(
                id = "orig_$idx",
                sourceUri = sourceUri,
                sourcePageIndex = idx,
                rotation = 0
            )
        }

        // 1. Drag-and-drop reorder: move page at index 0 ("ORIGINAL_PAGE_1") to index 2
        val reordered = com.example.ui.tools.PageOrganizerStateHelper.reorderPage(initialPages, fromIndex = 0, toIndex = 2)
        assertEquals(listOf(1, 2, 0, 3), reordered.map { it.sourcePageIndex })

        // 2. Rotate individual page (index 1 -> 90° CW) and batch pages (90° CW)
        val rotatedSingle = com.example.ui.tools.PageOrganizerStateHelper.rotatePageAt(reordered, index = 1, clockwise = true)
        assertEquals(90, rotatedSingle[1].rotation)
        val rotatedCCW = com.example.ui.tools.PageOrganizerStateHelper.rotatePageAt(rotatedSingle, index = 1, clockwise = false)
        assertEquals(0, rotatedCCW[1].rotation)

        val batchRotated = com.example.ui.tools.PageOrganizerStateHelper.rotatePages(
            rotatedSingle,
            targetIds = setOf(rotatedSingle[0].id, rotatedSingle[2].id),
            clockwise = true
        )
        assertEquals(90, batchRotated[0].rotation)
        assertEquals(90, batchRotated[2].rotation)

        // 3. Delete page at index 3 ("ORIGINAL_PAGE_4") and verify minimum 1-page protection
        val afterSingleDelete = com.example.ui.tools.PageOrganizerStateHelper.deletePageAt(batchRotated, index = 3)
        assertEquals(3, afterSingleDelete.size)
        assertEquals(listOf(1, 2, 0), afterSingleDelete.map { it.sourcePageIndex })

        val protectedDeleteAll = com.example.ui.tools.PageOrganizerStateHelper.deletePages(
            afterSingleDelete,
            targetIds = afterSingleDelete.map { it.id }.toSet()
        )
        assertEquals(1, protectedDeleteAll.size) // Keeps at least 1 page when all are selected

        // 4. Persist organized pages via PdfOperationEngine and verify output PDF page order & rotations
        val operationEngine = com.example.pdf.PdfOperationEngine(context)
        val organizedFile = operationEngine.saveOrganizedPages(
            originalUri = sourceUri,
            pages = afterSingleDelete,
            originalCachedFile = sourcePdf,
            onProgress = { _, _ -> }
        )
        assertTrue(organizedFile.exists() && organizedFile.length() > 0L)

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(organizedFile).use { savedDoc ->
            assertEquals(3, savedDoc.numberOfPages)
            assertEquals(90, savedDoc.getPage(0).rotation % 360)
            assertEquals(90, savedDoc.getPage(1).rotation % 360)
            assertEquals(90, savedDoc.getPage(2).rotation % 360)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = 1
            val page1Text = stripper.getText(savedDoc).replace(Regex("\\s+"), "")
            assertTrue("Expected ORIGINAL_PAGE_2 in $page1Text", page1Text.contains("ORIGINAL_PAGE_2"))
            stripper.startPage = 3
            stripper.endPage = 3
            val page3Text = stripper.getText(savedDoc).replace(Regex("\\s+"), "")
            assertTrue("Expected ORIGINAL_PAGE_1 in $page3Text", page3Text.contains("ORIGINAL_PAGE_1"))
        }

        // 5. Verify in-place page deletion and open document replacement in PdfDocumentManager
        val docManager = com.example.pdf.PdfDocumentManager(context)
        val openResult = docManager.openPdf(android.net.Uri.fromFile(organizedFile))
        assertTrue(openResult is com.example.pdf.PdfLoadResult.Success)
        assertEquals(3, docManager.totalPages)
        val afterViewerDeleteFile = docManager.deletePageInDocument(1)
        assertTrue(afterViewerDeleteFile != null && afterViewerDeleteFile.exists())
        assertEquals(2, docManager.totalPages)

        docManager.close()
        sourcePdf.delete()
        organizedFile.delete()
    }

    @Test
    fun `pdf password encryption and decryption with AES-256 AES-128 RC4-128 permissions and AES-GCM biometric vault test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // 1. Password generator & strength evaluator
        val generatedPass = com.example.pdf.PdfOptimizationEngine.generateStrongPassword(16)
        assertEquals(16, generatedPass.length)
        val strength = com.example.pdf.PdfOptimizationEngine.evaluatePasswordStrength(generatedPass)
        assertEquals(com.example.pdf.PasswordStrengthLevel.STRONG, strength.level)
        val weakStrength = com.example.pdf.PdfOptimizationEngine.evaluatePasswordStrength("123")
        assertEquals(com.example.pdf.PasswordStrengthLevel.WEAK, weakStrength.level)

        // 2. Create an unencrypted PDF and verify security status
        val plainPdf = File(context.cacheDir, "plain_security_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("CONFIDENTIAL CONTRACT 2026")
                cs.endText()
            }
            doc.save(plainPdf)
        }

        val engine = com.example.pdf.PdfOptimizationEngine(context)
        val initialStatus = engine.inspectSecurityStatus(plainPdf, null)
        assertEquals(false, initialStatus.isEncrypted)
        assertEquals(1, initialStatus.pageCount)

        // 3. Encrypt PDF with AES-256 and custom AccessPermissions
        val encryptedPdf = engine.encryptPdf(
            pdfFile = plainPdf,
            uri = null,
            userPassword = generatedPass,
            ownerPassword = "OwnerMaster#2026",
            canPrint = false,
            canCopy = false,
            canEdit = false,
            canAnnotate = true,
            canFillForms = true,
            algorithm = com.example.pdf.PdfEncryptionAlgorithm.AES_256,
            onProgress = { _, _ -> }
        )
        assertTrue(encryptedPdf.exists() && encryptedPdf.length() > 0L)

        // Verify locked status without password
        val lockedStatus = engine.inspectSecurityStatus(encryptedPdf, null)
        assertEquals(true, lockedStatus.isEncrypted)
        assertEquals(true, lockedStatus.requiresOpenPassword)

        // Verify status with valid password
        val unlockedStatus = engine.inspectSecurityStatus(encryptedPdf, null, password = generatedPass)
        assertEquals(true, unlockedStatus.isEncrypted)
        assertEquals(1, unlockedStatus.pageCount)

        // 4. Verify wrong password throws IllegalArgumentException on decrypt
        var wrongPasswordRejected = false
        try {
            engine.decryptPdf(encryptedPdf, null, "WrongPass123")
        } catch (e: IllegalArgumentException) {
            wrongPasswordRejected = true
        }
        assertTrue(wrongPasswordRejected)

        // 5. Decrypt PDF permanently with correct password and verify security is removed
        val decryptedPdf = engine.decryptPdf(
            pdfFile = encryptedPdf,
            uri = null,
            password = generatedPass
        )
        assertTrue(decryptedPdf.exists() && decryptedPdf.length() > 0L)
        val afterDecryptStatus = engine.inspectSecurityStatus(decryptedPdf, null)
        assertEquals(false, afterDecryptStatus.isEncrypted)
        assertEquals(1, afterDecryptStatus.pageCount)

        // 6. Verify AES-256-GCM Biometric Password Vault round-trip
        val lockManager = com.example.security.AppLockManager(context)
        val docUriStr = android.net.Uri.fromFile(encryptedPdf).toString()
        lockManager.saveDocumentPasswordForBiometrics(docUriStr, generatedPass)
        assertTrue(lockManager.hasSavedDocumentPasswordForBiometrics(docUriStr))
        assertEquals(generatedPass, lockManager.getSavedDocumentPasswordForBiometrics(docUriStr))
        lockManager.removeDocumentPasswordForBiometrics(docUriStr)
        assertEquals(false, lockManager.hasSavedDocumentPasswordForBiometrics(docUriStr))

        plainPdf.delete()
        encryptedPdf.delete()
        decryptedPdf.delete()
    }

    @Test
    fun `page thumbnail sidebar and bottom sheet filtering and quick navigation test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // 1. Verify ThumbnailNavigatorHelper filtering across ALL_PAGES, BOOKMARKED, ANNOTATED, and page number query
        val bookmarkedPages = setOf(1, 4, 7) // 0-indexed pages 2, 5, 8
        val annotatedPages = setOf(2, 4)     // 0-indexed pages 3, 5

        val allTen = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 10,
            filterMode = com.example.ui.ThumbnailFilterMode.ALL_PAGES,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPages
        )
        assertEquals((0 until 10).toList(), allTen)

        val filteredBookmarks = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 10,
            filterMode = com.example.ui.ThumbnailFilterMode.BOOKMARKED,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPages
        )
        assertEquals(listOf(1, 4, 7), filteredBookmarks)

        val filteredAnnotated = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 10,
            filterMode = com.example.ui.ThumbnailFilterMode.ANNOTATED,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPages
        )
        assertEquals(listOf(2, 4), filteredAnnotated)

        val queryPage5 = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 15,
            filterMode = com.example.ui.ThumbnailFilterMode.ALL_PAGES,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPages,
            pageQuery = "5"
        )
        // Pages whose 1-based display number contains "5" -> page 5 (index 4) and page 15 (index 14)
        assertEquals(listOf(4, 14), queryPage5)

        // 2. Verify PdfViewModel Thumbnail Sidebar and Bottom Sheet state transitions & quick page navigation
        val repository = com.example.data.PdfRepository(db.recentPdfDao(), db.bookmarkDao())
        val viewModel = com.example.ui.PdfViewModel(context, repository, settingsManager)
        assertEquals(false, viewModel.isThumbnailSheetOpen.value)
        assertEquals(false, viewModel.isThumbnailSidebarOpen.value)
        assertEquals(com.example.ui.ThumbnailFilterMode.ALL_PAGES, viewModel.thumbnailFilterMode.value)

        // Open bottom sheet, then dock to sidebar
        viewModel.setThumbnailSheetOpen(true)
        assertEquals(true, viewModel.isThumbnailSheetOpen.value)

        viewModel.switchThumbnailViewToSidebar()
        assertEquals(false, viewModel.isThumbnailSheetOpen.value)
        assertEquals(true, viewModel.isThumbnailSidebarOpen.value)

        // Switch from sidebar back to full grid bottom sheet
        viewModel.switchThumbnailViewToBottomSheet()
        assertEquals(false, viewModel.isThumbnailSidebarOpen.value)
        assertEquals(true, viewModel.isThumbnailSheetOpen.value)

        // Toggle sidebar and filter mode
        viewModel.toggleThumbnailSidebar()
        assertEquals(true, viewModel.isThumbnailSidebarOpen.value)
        viewModel.setThumbnailFilterMode(com.example.ui.ThumbnailFilterMode.BOOKMARKED)
        assertEquals(com.example.ui.ThumbnailFilterMode.BOOKMARKED, viewModel.thumbnailFilterMode.value)

        // Open a 5-page PDF and verify page navigation updates currentPage
        val fivePagePdf = File(context.cacheDir, "thumb_nav_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            repeat(5) { idx ->
                val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Thumbnail Navigation Page ${idx + 1}")
                    cs.endText()
                }
            }
            doc.save(fivePagePdf)
        }

        viewModel.onPageChanged(3)
        assertEquals(3, viewModel.currentPage.value)

        // Closing viewer resets both thumbnail sheet, sidebar, and filter mode
        viewModel.closeViewer()
        assertEquals(false, viewModel.isThumbnailSheetOpen.value)
        assertEquals(false, viewModel.isThumbnailSidebarOpen.value)
        assertEquals(com.example.ui.ThumbnailFilterMode.ALL_PAGES, viewModel.thumbnailFilterMode.value)

        fivePagePdf.delete()
    }

    @Test
    fun `mainActivity launches, handles lifecycle transitions, intent viewing, and foreground service operations without crashing`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // 1. Create a real sample PDF to test ACTION_VIEW intent handling in MainActivity
        val launchPdf = File(context.cacheDir, "launch_test_doc.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("Crash Free Launch Verification 2026")
                cs.endText()
            }
            doc.save(launchPdf)
        }

        // 2. Build and launch MainActivity through full Android lifecycle (create -> start -> resume -> pause -> resume -> stop -> destroy)
        val viewIntent = android.content.Intent(context, MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_VIEW
            data = android.net.Uri.fromFile(launchPdf)
        }
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, viewIntent).setup()
        val activity = controller.get()
        assertNotNull(activity)

        // Exercise pause and resume lifecycle hooks (AppLockManager + DirectApkUpdateManager)
        controller.pause().resume()

        // Exercise PdfOperationService start / updateProgress / stop to ensure no ForegroundService exceptions occur
        com.example.pdf.PdfOperationService.start(context, "Testing Service", "Starting background task...")
        com.example.pdf.PdfOperationService.updateProgress(context, "Testing Service", "50% complete", 50)
        com.example.pdf.PdfOperationService.stop(context)

        controller.stop().destroy()
        launchPdf.delete()
    }

    @Test
    fun `bengali 26 popular fonts catalog, broken matra and Bijoy normalizer, Word DOCX fontTable and searchable PDF OCR layer work end to end`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        // 1. Verify 26+ popular Bengali fonts are registered and accessible
        val allBengaliFonts = com.example.pdf.BengaliFontOption.entries
        assertEquals(true, allBengaliFonts.size >= 26)
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("Kalpurush"))
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("SolaimanLipi"))
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("Nikosh"))
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("SutonnyMJ"))
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("Siyam Rupali"))
        assertNotNull(com.example.pdf.BengaliFontOption.detectFromPdfFontName("Hind Siliguri"))

        // 2. Verify broken visual-order Bengali vowel sign (ি, ে, ৈ) & nukta normalization
        // Visual order: "\u09BF\u09AC\u099C\u09AF\u09BC" (ি + ব + জ + য + ়) -> Logical Unicode: "বিজয়" ("\u09AC\u09BF\u099C\u09DF")
        val brokenVisualMatra = "\u09BF\u09AC\u099C\u09AF\u09BC"
        val fixedMatra = com.example.pdf.BengaliTextNormalizer.fixUnicodeBengaliOrderAndNuktas(brokenVisualMatra)
        assertEquals("বিজয়", fixedMatra)

        // Visual split O-kar: "\u09C7\u09B8\u09BE\u09A8\u09BE\u09B0" (ে + স + া + ন + া + র) -> "সোনার"
        val brokenSplitOKar = "\u09C7\u09B8\u09BE\u09A8\u09BE\u09B0"
        val fixedOKar = com.example.pdf.BengaliTextNormalizer.fixUnicodeBengaliOrderAndNuktas(brokenSplitOKar)
        assertEquals("সোনার", fixedOKar)

        // 3. Verify Legacy Bijoy / ANSI (SutonnyMJ) to Unicode conversion
        // "Avgvi †mvbvi evsjv" -> "আমার সোনার বাংলা"
        val bijoySample = "Avgvi †mvbvi evsjv"
        assertEquals(true, com.example.pdf.BengaliTextNormalizer.looksLikeBijoyAnsiText(bijoySample))
        val convertedUnicode = com.example.pdf.BengaliTextNormalizer.convertBijoyToUnicode(bijoySample)
        assertEquals("আমার সোনার বাংলা", convertedUnicode)

        // 4. Create a PDF with embedded Bengali text & Bijoy text, convert to Word (.docx) with SolaimanLipi,
        // and verify word/fontTable.xml, word/styles.xml, and word/document.xml
        val bengaliPdf = File(context.cacheDir, "bengali_conversion_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            val bnFont = com.example.pdf.BengaliFontOption.NOTO_SANS_BENGALI.loadPdfBoxFont(context, doc)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(bnFont, 14f)
                cs.newLineAtOffset(72f, 720f)
                cs.showText("আমার সোনার বাংলা")
                cs.endText()
            }
            doc.save(bengaliPdf)
        }

        val converter = com.example.pdf.PdfDocumentConverterEngine(context)
        val docxFile = converter.convertPdfDocument(
            sourceFile = bengaliPdf,
            sourceUri = android.net.Uri.fromFile(bengaliPdf),
            sourceFileName = "bengali_conversion_test.pdf",
            config = com.example.pdf.PdfConversionConfig(
                format = com.example.pdf.PdfExportFormat.WORD_DOCX,
                targetBengaliFont = com.example.pdf.BengaliFontOption.SOLAIMAN_LIPI,
                fixBengaliFontEncoding = true,
                convertBijoyToUnicode = true
            )
        )
        assertEquals(true, docxFile.exists() && docxFile.length() > 0L)

        var fontTableXml = ""
        var documentXml = ""
        java.util.zip.ZipFile(docxFile).use { zip ->
            val ftEntry = zip.getEntry("word/fontTable.xml")
            assertNotNull(ftEntry)
            fontTableXml = zip.getInputStream(ftEntry).bufferedReader().readText()

            val docEntry = zip.getEntry("word/document.xml")
            assertNotNull(docEntry)
            documentXml = zip.getInputStream(docEntry).bufferedReader().readText()
        }
        assertEquals(true, fontTableXml.contains("SolaimanLipi"))
        assertEquals(true, fontTableXml.contains("Kalpurush"))
        assertEquals(true, fontTableXml.contains("Nikosh"))
        assertEquals(true, fontTableXml.contains("SutonnyMJ"))
        assertEquals(true, documentXml.contains("SolaimanLipi"))
        assertEquals(true, documentXml.contains("আমার সোনার বাংলা"))

        // 5. Verify ScanOcrEngine embeds Bengali Unicode words into searchable PDF without stripping them
        val ocrEngine = com.example.scan.ScanOcrEngine(context)
        val searchablePdf = ocrEngine.embedSearchableOcrIntoPdf(
            sourceFile = bengaliPdf,
            sourceUri = android.net.Uri.fromFile(bengaliPdf),
            outputFileName = "bengali_searchable_out.pdf",
            cachedPageResults = mapOf(
                0 to com.example.scan.OcrPageResult(
                    fullText = "বাংলাদেশ সোনার বাংলা",
                    words = listOf(
                        com.example.scan.OcrWordBox("বাংলাদেশ", 0.1f, 0.2f, 0.3f, 0.25f),
                        com.example.scan.OcrWordBox("সোনার", 0.32f, 0.2f, 0.5f, 0.25f)
                    )
                )
            ),
            bengaliFont = com.example.pdf.BengaliFontOption.KALPURUSH
        )
        assertEquals(true, searchablePdf.exists() && searchablePdf.length() > 0L)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(searchablePdf).use { verifyDoc ->
            val extracted = com.tom_roush.pdfbox.text.PDFTextStripper().getText(verifyDoc)
            assertEquals(true, extracted.contains("বাংলাদেশ"))
        }

        bengaliPdf.delete()
        docxFile.delete()
        searchablePdf.delete()
    }

    @Test
    fun `pdf summarization structured json parser, markdown and bengali heading parser, bengali page range, chunk budget sampler, ocr text extraction, and summary export work end to end`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val aiEngine = com.example.ai.PdfAiAssistantEngine(context)

        // 1. Verify Structured JSON response parsing (primary path used with Schema.obj)
        val jsonRaw = """
            {
              "shortSummary": "This quarterly report highlights a 24% increase in renewable energy adoption and strong fiscal discipline [Page 1].",
              "keyPoints": [
                "Solar capacity expanded by 140 MW across regional grids [Page 1].",
                "Operating expenses dropped by 8% due to automated maintenance [Page 2].",
                "Projected Q4 dividend growth remains on track at 12% [Page 3]."
              ],
              "referencedPages": [1, 2, 3]
            }
        """.trimIndent()

        val parsedJsonSummary = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = jsonRaw,
            pageRangeDescription = "Pages 1–3",
            outputLanguage = com.example.ai.AiOutputLanguage.ENGLISH,
            fallbackPages = listOf(1)
        )
        assertTrue(parsedJsonSummary.shortSummary.contains("24% increase in renewable energy"))
        assertEquals(3, parsedJsonSummary.keyPoints.size)
        assertEquals(listOf(1, 2, 3), parsedJsonSummary.referencedPages)

        // 2. Verify Markdown heading response parsing (**SHORT_SUMMARY** on its own line) never sets shortSummary to the header
        val markdownRaw = """
            ### **SHORT_SUMMARY**
            
            The contract establishes a 3-year software licensing agreement between Alpha Corp and Beta Ltd [Page 1].
            
            ### **KEY_POINTS**
            - **Annual license fee**: Fixed at $50,000 per year with 99.9% SLA uptime [Page 1].
            - **Data privacy**: All customer records must remain encrypted with AES-256 [Page 2].
            - **Termination clause**: Either party may terminate with 60 days written notice [Page 4].
        """.trimIndent()

        val parsedMarkdownSummary = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = markdownRaw,
            pageRangeDescription = "Pages 1–4",
            outputLanguage = com.example.ai.AiOutputLanguage.ENGLISH,
            fallbackPages = listOf(1, 2, 4)
        )
        assertEquals(false, parsedMarkdownSummary.shortSummary.contains("SHORT_SUMMARY", ignoreCase = true))
        assertTrue(parsedMarkdownSummary.shortSummary.contains("3-year software licensing agreement"))
        assertEquals(3, parsedMarkdownSummary.keyPoints.size)
        assertEquals(listOf(1, 2, 4), parsedMarkdownSummary.referencedPages)

        // 3. Verify Bengali localized headings (**সংক্ষিপ্ত সারসংক্ষেপ:** / **মূল পয়েন্টসমূহ:**)
        val bengaliRaw = """
            **সংক্ষিপ্ত সারসংক্ষেপ:**
            এই প্রতিবেদনে ২০২৬ অর্থবছরের জাতীয় বাজেট এবং শিক্ষা খাতে বরাদ্দ বৃদ্ধির বিস্তারিত আলোচনা করা হয়েছে [পৃষ্ঠা ১]।
            
            **মূল পয়েন্টসমূহ:**
            • প্রাথমিক ও কারিগরি শিক্ষায় ১৫% অতিরিক্ত বাজেট বরাদ্দ করা হয়েছে [পৃষ্ঠা ২]।
            • গ্রামীণ ডিজিটাল স্বাস্থ্যসেবা কেন্দ্রে নতুন সৌরবিদ্যুৎ সংযোগ স্থাপন [পৃষ্ঠা ৩]।
        """.trimIndent()

        val parsedBengaliSummary = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = bengaliRaw,
            pageRangeDescription = "Whole document (3 pages)",
            outputLanguage = com.example.ai.AiOutputLanguage.BENGALI,
            fallbackPages = listOf(1, 2, 3)
        )
        assertTrue(parsedBengaliSummary.shortSummary.contains("২০২৬ অর্থবছরের জাতীয় বাজেট"))
        assertEquals(2, parsedBengaliSummary.keyPoints.size)
        assertEquals(listOf(1, 2, 3), parsedBengaliSummary.referencedPages)

        // 4. Verify Bengali digits ("১-৩, ৫") and en-dash ("1–3") in parsePageRangeInput
        val bengaliPageRange = com.example.ai.PdfAiAssistantEngine.parsePageRangeInput("১–৩, ৫", totalPages = 6)
        assertEquals(listOf(0, 1, 2, 4), bengaliPageRange)

        // 5. Verify sampleChunksWithinBudget samples proportionally across 20 chunks instead of capping at 8
        val twentyChunks = (1..20).map { pageNum: Int ->
            com.example.ai.PdfTextChunk(
                chunkId = "p${pageNum}_c0",
                pageIndex = pageNum - 1,
                pageNumber = pageNum,
                text = "Detailed section content for page $pageNum with approximately one hundred characters of text."
            )
        }
        val sampledChunks = com.example.ai.PdfAiAssistantEngine.sampleChunksWithinBudget(
            chunks = twentyChunks,
            maxTotalChars = 14_000
        )
        assertEquals(20, sampledChunks.size)

        // 6. Verify extractPagesText falls back to existingOcrText for scanned pages and normalizes Bijoy text
        val scannedPdf = File(context.cacheDir, "summary_scanned_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            // Page 1: Blank/scanned page (no embedded PDF text)
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
            // Page 2: Embedded legacy Bijoy text ("Avgvi †mvbvi evsjv")
            val page2 = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page2)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page2).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("Avgvi †mvbvi evsjv National Report 2026")
                cs.endText()
            }
            doc.save(scannedPdf)
        }

        val extractedPages = aiEngine.extractPagesText(
            cachedFile = scannedPdf,
            uri = android.net.Uri.fromFile(scannedPdf),
            pageIndices = listOf(0, 1),
            existingOcrText = mapOf(0 to "OCR Recognized Text from Scanned Page 1: Annual Revenue $12.4M"),
            autoOcrScannedPages = false
        )
        assertTrue(extractedPages[0].orEmpty().contains("OCR Recognized Text from Scanned Page 1"))
        assertTrue(extractedPages[1].orEmpty().contains("আমার সোনার বাংলা"))

        // 7. Verify exportSummaryAsTxt and exportSummaryAsPdf (with Bengali font KALPURUSH)
        val summaryTxtFile = File(context.cacheDir, "summary_export_test.txt")
        val txtOk = aiEngine.exportSummaryAsTxt(parsedBengaliSummary, summaryTxtFile)
        assertTrue(txtOk && summaryTxtFile.exists())
        val exportedTxtContent = summaryTxtFile.readText()
        assertTrue(exportedTxtContent.contains("২০২৬ অর্থবছরের জাতীয় বাজেট"))
        assertTrue(exportedTxtContent.contains(com.example.ai.AI_DISCLAIMER_TEXT))

        val summaryPdfFile = File(context.cacheDir, "summary_export_test.pdf")
        val pdfOk = aiEngine.exportSummaryAsPdf(
            result = parsedBengaliSummary,
            outputFile = summaryPdfFile,
            selectedBengaliFont = com.example.pdf.BengaliFontOption.KALPURUSH
        )
        assertTrue(pdfOk && summaryPdfFile.exists() && summaryPdfFile.length() > 0L)

        scannedPdf.delete()
        summaryTxtFile.delete()
        summaryPdfFile.delete()
    }

    @Test
    fun `ml kit ocr optional language packs download, storage management, multi-script post-processing, and scan ocr engine integration test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val packManager = com.example.scan.OcrLanguagePackManager(context)
        packManager.deleteAllOptionalPacks()

        // 1. Verify all 14 OCR language packs are defined and AUTO/ENGLISH are BuiltIn
        val allPacks = com.example.scan.OcrLanguagePack.entries
        assertEquals(14, allPacks.size)
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.AUTO))
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.ENGLISH))
        assertEquals(false, packManager.isPackInstalled(com.example.scan.OcrLanguagePack.BENGALI))
        assertEquals(false, packManager.isPackInstalled(com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI))
        assertEquals(false, packManager.isPackInstalled(com.example.scan.OcrLanguagePack.CHINESE))

        // 2. Test downloading optional language packs (Bengali, Hindi/Devanagari, Chinese, Arabic, Spanish)
        val progressUpdates = mutableListOf<Float>()
        val bnSuccess = packManager.downloadLanguagePack(
            pack = com.example.scan.OcrLanguagePack.BENGALI,
            onProgress = { progressUpdates.add(it) }
        )
        assertTrue(bnSuccess)
        assertTrue(progressUpdates.isNotEmpty())
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.BENGALI))
        val bnStatus = packManager.packStatuses.value[com.example.scan.OcrLanguagePack.BENGALI]
        assertTrue(bnStatus is com.example.scan.OcrPackInstallStatus.Installed)

        // Download bundle of Hindi + Chinese + Arabic
        val bundleCount = packManager.downloadLanguagePacks(
            listOf(
                com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI,
                com.example.scan.OcrLanguagePack.CHINESE,
                com.example.scan.OcrLanguagePack.ARABIC
            )
        )
        assertEquals(3, bundleCount)
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI))
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.CHINESE))
        assertTrue(packManager.isPackInstalled(com.example.scan.OcrLanguagePack.ARABIC))
        assertEquals(4, packManager.getDownloadedOptionalPackCount())
        assertTrue(packManager.getTotalInstalledStorageBytes() > 0L)

        // 3. Verify active engine resolution for AUTO and specific language packs
        val autoEngines = packManager.resolveActiveEnginesForOcr(com.example.scan.OcrLanguagePack.AUTO)
        assertTrue(autoEngines.contains(com.example.scan.OcrScriptEngine.LATIN))
        assertTrue(autoEngines.contains(com.example.scan.OcrScriptEngine.DEVANAGARI))
        assertTrue(autoEngines.contains(com.example.scan.OcrScriptEngine.CHINESE))

        val jpEngines = packManager.resolveActiveEnginesForOcr(com.example.scan.OcrLanguagePack.JAPANESE)
        assertTrue(jpEngines.contains(com.example.scan.OcrScriptEngine.JAPANESE))
        assertTrue(jpEngines.contains(com.example.scan.OcrScriptEngine.LATIN))

        // 4. Verify language-specific OCR post-processing & script detection across non-English languages
        // 4a. Bengali broken visual matra & Bijoy repair
        val rawBengali = "\u09BF\u09AC\u099C\u09AF\u09BC"
        val postBengali = packManager.postProcessRecognizedText(rawBengali, com.example.scan.OcrLanguagePack.BENGALI)
        assertEquals("বিজয়", postBengali)
        assertTrue(packManager.detectRecognizedLanguageLabel(postBengali).contains("Bengali"))

        // 4b. Devanagari precomposed nukta & candrabindu normalization
        val rawHindi = "क\u093Cलम भारत"
        val postHindi = packManager.postProcessRecognizedText(rawHindi, com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI)
        assertTrue(postHindi.contains('\u0958'))
        assertTrue(packManager.detectRecognizedLanguageLabel(postHindi).contains("Devanagari"))

        // 4c. Arabic/Urdu Lam-Alef presentation form normalization
        val rawArabic = "\uFEFB مرحبا"
        val postArabic = packManager.postProcessRecognizedText(rawArabic, com.example.scan.OcrLanguagePack.ARABIC)
        assertTrue(postArabic.startsWith("لا"))
        assertTrue(packManager.detectRecognizedLanguageLabel(postArabic).contains("Arabic"))

        // 4d. CJK unwanted intra-character space cleanup & full-width punctuation
        val rawChinese = "中 国 人 民 银 行 , 2026"
        val postChinese = packManager.postProcessRecognizedText(rawChinese, com.example.scan.OcrLanguagePack.CHINESE)
        assertEquals("中国人民银行，2026", postChinese)
        assertTrue(packManager.detectRecognizedLanguageLabel(postChinese).contains("CJK"))

        // 4e. European ligature normalization
        val rawGerman = "Stra\uFB02e & \uFB01nanzbericht"
        val postGerman = packManager.postProcessRecognizedText(rawGerman, com.example.scan.OcrLanguagePack.GERMAN)
        assertEquals("k" != postGerman && postGerman.contains("fl") && postGerman.contains("fi"), true)

        // 5. Verify ScanOcrEngine integration with non-English PDF page & selected language pack
        packManager.selectLanguagePack(com.example.scan.OcrLanguagePack.BENGALI)
        assertEquals(com.example.scan.OcrLanguagePack.BENGALI, packManager.selectedLanguagePack.value)

        val multiLangPdf = File(context.cacheDir, "multilang_ocr_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            val bnFont = com.example.pdf.BengaliFontOption.NOTO_SANS_BENGALI.loadPdfBoxFont(context, doc)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(bnFont, 14f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("বাংলাদেশ ব্যাংক বার্ষিক প্রতিবেদন")
                cs.endText()
            }
            doc.save(multiLangPdf)
        }

        val ocrEngine = com.example.scan.ScanOcrEngine(context)
        val ocrPageRes = ocrEngine.recognizeTextFromPdfPage(
            pdfFile = multiLangPdf,
            uri = android.net.Uri.fromFile(multiLangPdf),
            pageIndex = 0,
            languagePack = com.example.scan.OcrLanguagePack.BENGALI
        )
        assertEquals(com.example.scan.OcrLanguagePack.BENGALI, ocrPageRes.languagePackUsed)
        assertTrue(ocrPageRes.fullText.contains("বাংলাদেশ"))
        assertTrue(ocrPageRes.detectedLanguageLabel.contains("Bengali"))

        // 6. Test deleting a single pack and resetting to AUTO
        val deletedHindi = packManager.deleteLanguagePack(com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI)
        assertTrue(deletedHindi)
        assertEquals(false, packManager.isPackInstalled(com.example.scan.OcrLanguagePack.HINDI_DEVANAGARI))

        // Deleting currently selected pack resets selection to AUTO
        val deletedBengali = packManager.deleteLanguagePack(com.example.scan.OcrLanguagePack.BENGALI)
        assertTrue(deletedBengali)
        assertEquals(com.example.scan.OcrLanguagePack.AUTO, packManager.selectedLanguagePack.value)

        packManager.deleteAllOptionalPacks()
        assertEquals(0, packManager.getDownloadedOptionalPackCount())
        multiLangPdf.delete()
    }

    @Test
    fun `rotate selected pdf pages by 90 degree increments and persist to document file test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val engine = com.example.pdf.PdfOperationEngine(context)

        // 1. Verify 90-degree increment normalization helper
        assertEquals(90, com.example.pdf.RotationIncrement.normalizeTo90Step(90))
        assertEquals(270, com.example.pdf.RotationIncrement.normalizeTo90Step(-90))
        assertEquals(180, com.example.pdf.RotationIncrement.normalizeTo90Step(180))
        assertEquals(180, com.example.pdf.RotationIncrement.normalizeTo90Step(-180))
        assertEquals(0, com.example.pdf.RotationIncrement.normalizeTo90Step(360))
        assertEquals(90, com.example.pdf.RotationIncrement.normalizeTo90Step(450))
        assertEquals(90, com.example.pdf.RotationIncrement.normalizeTo90Step(-270))

        // 2. Create a 4-page PDF document file on disk
        val docFile = File(context.cacheDir, "rotate_selected_pages_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            for (i in 0 until 4) {
                val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                page.rotation = 0
                doc.addPage(page)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 14f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Rotate Test Page ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(docFile)
        }
        assertTrue(docFile.exists() && docFile.length() > 0L)

        // 3. Rotate selected pages with distinct 90-degree increments and overwrite source document file:
        //    Page 0 -> +90° (CW)
        //    Page 1 -> -90° (CCW -> 270°)
        //    Page 2 -> +180° (Flip)
        //    Page 3 -> not selected (stays 0°)
        val savedInPlace = engine.rotateSelectedPages(
            sourceFile = docFile,
            sourceUri = android.net.Uri.fromFile(docFile),
            pageRotationDeltas = mapOf(
                0 to com.example.pdf.RotationIncrement.CLOCKWISE_90.degreesDelta,
                1 to com.example.pdf.RotationIncrement.COUNTER_CLOCKWISE_90.degreesDelta,
                2 to com.example.pdf.RotationIncrement.FLIP_180.degreesDelta
            ),
            overwriteSourceFile = true
        )
        assertEquals(docFile.absolutePath, savedInPlace.absolutePath)

        // Verify rotations were saved directly to the document file on disk
        val pdfBoxHelper = com.example.pdf.PdfBoxHelper(context)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(docFile).use { reloaded ->
            assertEquals(4, reloaded.numberOfPages)
            assertEquals(90, reloaded.getPage(0).rotation)
            assertEquals(270, reloaded.getPage(1).rotation)
            assertEquals(180, reloaded.getPage(2).rotation)
            assertEquals(0, reloaded.getPage(3).rotation)

            val page0Text = pdfBoxHelper.extractSinglePageTextRotationAware(reloaded, 0)
            val page3Text = pdfBoxHelper.extractSinglePageTextRotationAware(reloaded, 3)
            assertTrue(page0Text.contains("Rotate Test Page 1"))
            assertTrue(page3Text.contains("Rotate Test Page 4"))
        }

        // 4. Parse custom page range ("1-2, 4" -> indices 0, 1, 3) and apply another +90° increment into a new file
        val rangeIndices = engine.parseFlatPageIndices("1-2, 4", 4)
        assertEquals(listOf(0, 1, 3), rangeIndices)

        val newCopyFile = File(context.cacheDir, "rotate_selected_pages_copy.pdf")
        val savedCopy = engine.rotateSelectedPages(
            sourceFile = docFile,
            sourceUri = android.net.Uri.fromFile(docFile),
            pageIndices = rangeIndices,
            rotationIncrementDegrees = 90,
            outputFile = newCopyFile,
            overwriteSourceFile = false
        )
        assertEquals(newCopyFile.absolutePath, savedCopy.absolutePath)
        assertTrue(newCopyFile.exists() && newCopyFile.length() > 0L)

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(newCopyFile).use { reloadedCopy ->
            assertEquals(4, reloadedCopy.numberOfPages)
            // Page 0 was 90°, +90° -> 180°
            assertEquals(180, reloadedCopy.getPage(0).rotation)
            // Page 1 was 270°, +90° -> 0°
            assertEquals(0, reloadedCopy.getPage(1).rotation)
            // Page 2 was not in "1-2, 4", stays 180°
            assertEquals(180, reloadedCopy.getPage(2).rotation)
            // Page 3 was 0°, +90° -> 90°
            assertEquals(90, reloadedCopy.getPage(3).rotation)
        }

        docFile.delete()
        newCopyFile.delete()
    }

    @Test
    fun `merge documents and rotate selected pages dialog ui stability test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)

        val engine = com.example.pdf.PdfOperationEngine(context)

        // 1. Create two 2-page PDFs with text streams and merge them to ensure no COSStream closed crash
        val pdf1 = File(context.cacheDir, "merge_part1.pdf")
        val pdf2 = File(context.cacheDir, "merge_part2.pdf")
        listOf(pdf1 to "DocA", pdf2 to "DocB").forEach { (file, prefix) ->
            com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
                for (i in 1..2) {
                    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    doc.addPage(page)
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                        cs.beginText()
                        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                        cs.newLineAtOffset(60f, 720f)
                        cs.showText("$prefix Page $i")
                        cs.endText()
                    }
                }
                doc.save(file)
            }
        }

        val mergedFile = engine.mergeDocuments(
            items = listOf(
                com.example.pdf.MergeItem(
                    id = "1",
                    uri = android.net.Uri.fromFile(pdf1),
                    fileName = pdf1.name,
                    isImage = false,
                    pageRangeText = "1-2"
                ),
                com.example.pdf.MergeItem(
                    id = "2",
                    uri = android.net.Uri.fromFile(pdf2),
                    fileName = pdf2.name,
                    isImage = false,
                    pageRangeText = "1-2"
                )
            ),
            outputName = "Merged_Stability.pdf",
            onProgress = { _, _ -> }
        )
        assertTrue(mergedFile.exists() && mergedFile.length() > 0L)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(mergedFile).use { doc ->
            assertEquals(4, doc.numberOfPages)
        }

        // 2. Verify PdfDocumentManager and PdfViewModel open the merged PDF and execute in-place 90° page rotations cleanly without crashing
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.example.data.PdfRepository(db.recentPdfDao(), db.bookmarkDao())
        val viewModel = com.example.ui.PdfViewModel(app, repo, settingsManager)

        val mergedUri = android.net.Uri.fromFile(mergedFile)
        viewModel.openPdf(mergedUri)
        repeat(10) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(30)
        }

        viewModel.executeRotateSelectedPages(
            sourceFile = mergedFile,
            sourceUri = mergedUri,
            sourceName = mergedFile.name,
            pageRotationDeltas = mapOf(0 to 90, 1 to -90, 2 to 180, 3 to 90),
            overwriteOriginal = true,
            outputName = mergedFile.name
        )

        // Pump Robolectric main looper while background IO operation runs
        var attempts = 0
        while (viewModel.operationProgress.value !is com.example.pdf.OperationProgress.Success &&
            viewModel.operationProgress.value !is com.example.pdf.OperationProgress.Failure &&
            attempts < 60
        ) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val finalProgress = viewModel.operationProgress.value
        assertTrue("Expected Success but got $finalProgress", finalProgress is com.example.pdf.OperationProgress.Success)

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(mergedFile).use { doc ->
            assertEquals(4, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(270, doc.getPage(1).rotation)
            assertEquals(180, doc.getPage(2).rotation)
            assertEquals(90, doc.getPage(3).rotation)
        }

        viewModel.closeViewer()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        pdf1.delete()
        pdf2.delete()
        mergedFile.delete()
    }

    @Test
    fun `bottom thumbnail drawer and long document section navigation test`(): Unit = runBlocking {
        // 1. Verify automatic chunked sections for a 35-page long document without outline chapters
        val chunkedSections = com.example.ui.ThumbnailNavigatorHelper.buildDocumentSections(
            totalPages = 35,
            outlineItems = emptyList(),
            maxPagesPerChunk = 10
        )
        assertEquals(4, chunkedSections.size)
        assertEquals(0, chunkedSections[0].startPage)
        assertEquals(9, chunkedSections[0].endPage)
        assertEquals("P.1–10", chunkedSections[0].subtitle)
        assertEquals(10, chunkedSections[1].startPage)
        assertEquals(19, chunkedSections[1].endPage)
        assertEquals(30, chunkedSections[3].startPage)
        assertEquals(34, chunkedSections[3].endPage)
        assertEquals("P.31–35", chunkedSections[3].subtitle)

        // Active section lookup
        val activeAtPage15 = com.example.ui.ThumbnailNavigatorHelper.findActiveSection(chunkedSections, 15)
        assertEquals(chunkedSections[1].id, activeAtPage15?.id)

        // Filter pages by section + bookmark filter
        val section2Pages = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 35,
            filterMode = com.example.ui.ThumbnailFilterMode.ALL_PAGES,
            bookmarkedPages = setOf(2, 12, 18, 31),
            annotatedPages = emptySet(),
            sectionFilter = chunkedSections[1]
        )
        assertEquals((10..19).toList(), section2Pages)

        val section2Bookmarked = com.example.ui.ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = 35,
            filterMode = com.example.ui.ThumbnailFilterMode.BOOKMARKED,
            bookmarkedPages = setOf(2, 12, 18, 31),
            annotatedPages = emptySet(),
            sectionFilter = chunkedSections[1]
        )
        assertEquals(listOf(12, 18), section2Bookmarked)

        // 2. Verify outline-based sections for a 25-page document with Table of Contents chapters
        val outlineEntries = listOf(
            com.example.pdf.PdfOutlineEntry(title = "Introduction", pageIndex = 2, depth = 0),
            com.example.pdf.PdfOutlineEntry(title = "Architecture", pageIndex = 10, depth = 0),
            com.example.pdf.PdfOutlineEntry(title = "Appendix", pageIndex = 18, depth = 0)
        )
        val outlineSections = com.example.ui.ThumbnailNavigatorHelper.buildDocumentSections(
            totalPages = 25,
            outlineItems = outlineEntries
        )
        assertEquals(4, outlineSections.size)
        assertEquals("Front Matter", outlineSections[0].title)
        assertEquals(0, outlineSections[0].startPage)
        assertEquals(1, outlineSections[0].endPage)
        assertEquals("Introduction", outlineSections[1].title)
        assertEquals(2, outlineSections[1].startPage)
        assertEquals(9, outlineSections[1].endPage)
        assertEquals("Architecture", outlineSections[2].title)
        assertEquals(10, outlineSections[2].startPage)
        assertEquals(17, outlineSections[2].endPage)
        assertEquals("Appendix", outlineSections[3].title)
        assertEquals(18, outlineSections[3].startPage)
        assertEquals(24, outlineSections[3].endPage)

        // 3. Verify PdfViewModel bottom thumbnail drawer state transitions
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.example.data.PdfRepository(db.recentPdfDao(), db.bookmarkDao())
        val viewModel = com.example.ui.PdfViewModel(app, repo, settingsManager)

        assertEquals(false, viewModel.isThumbnailBottomDrawerOpen.value)
        viewModel.toggleThumbnailBottomDrawer()
        assertEquals(true, viewModel.isThumbnailBottomDrawerOpen.value)

        viewModel.setSelectedSectionId(outlineSections[2].id)
        assertEquals(outlineSections[2].id, viewModel.selectedSectionId.value)

        viewModel.switchBottomDrawerToSheet()
        assertEquals(false, viewModel.isThumbnailBottomDrawerOpen.value)
        assertEquals(true, viewModel.isThumbnailSheetOpen.value)

        viewModel.switchSheetToBottomDrawer()
        assertEquals(false, viewModel.isThumbnailSheetOpen.value)
        assertEquals(true, viewModel.isThumbnailBottomDrawerOpen.value)

        viewModel.closeViewer()
        assertEquals(false, viewModel.isThumbnailBottomDrawerOpen.value)
        assertEquals(null, viewModel.selectedSectionId.value)
    }

    @Test
    fun `firebase app check initialization and pdf summarization parser and extraction test`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify configureAppCheck safely initializes and persists debug token from Intent without crashing
        val debugIntent = android.content.Intent().apply {
            putExtra("app_check_debug_token", "12345678-1234-1234-1234-123456789abc")
        }
        com.example.ai.configureAppCheck(context, debugIntent)
        // Calling a second time without intent extras should be idempotent and not fail
        com.example.ai.configureAppCheck(context, null)

        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${com.google.firebase.FirebaseApp.getInstance().persistenceKey}",
            Context.MODE_PRIVATE
        )
        assertEquals(
            "12345678-1234-1234-1234-123456789abc",
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )

        // 2. Verify structured JSON summary parsing with object items in keyPoints and citation extraction
        val jsonWithObjectKeyPoints = """
            ```json
            {
              "shortSummary": "This quarterly report outlines revenue growth to $48M [Page 1]. Operating expenses decreased by 12% due to automation [Page 3].",
              "keyPoints": [
                {"point": "Total Q3 revenue reached $48M, up 22% YoY [Page 1]."},
                {"text": "Automation reduced operating costs by 12% [Page 3]."},
                "Net profit margin expanded to 19.5% [Page 4]."
              ],
              "referencedPages": [1, 3, 4]
            }
            ```
        """.trimIndent()

        val parsedJson = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = jsonWithObjectKeyPoints,
            pageRangeDescription = "All Pages (1-5)",
            outputLanguage = com.example.ai.AiOutputLanguage.ENGLISH,
            fallbackPages = listOf(1, 2, 3, 4, 5)
        )
        assertEquals(true, parsedJson.shortSummary.contains("revenue growth to $48M"))
        assertEquals(3, parsedJson.keyPoints.size)
        assertEquals(true, parsedJson.keyPoints[0].contains("Total Q3 revenue reached $48M"))
        assertEquals(true, parsedJson.keyPoints[1].contains("Automation reduced operating costs"))
        assertEquals(listOf(1, 3, 4), parsedJson.referencedPages)

        // 3. Verify JSON summary with empty keyPoints array automatically derives bullet points from summary sentences
        val jsonEmptyKeyPoints = """
            {
              "shortSummary": "বাংলাদেশের অর্থনৈতিক প্রবৃদ্ধি ২০২৬ সালে উল্লেখযোগ্য হারে বৃদ্ধি পেয়েছে [Page 1]। রপ্তানি আয় এবং ডিজিটাল ব্যাংকিং সেবা সম্প্রসারিত হয়েছে [Page 2]।",
              "keyPoints": []
            }
        """.trimIndent()
        val parsedEmptyPoints = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = jsonEmptyKeyPoints,
            pageRangeDescription = "Pages 1-2",
            outputLanguage = com.example.ai.AiOutputLanguage.BENGALI,
            fallbackPages = listOf(1, 2)
        )
        assertEquals(true, parsedEmptyPoints.shortSummary.contains("বাংলাদেশের অর্থনৈতিক প্রবৃদ্ধি"))
        assertEquals(true, parsedEmptyPoints.keyPoints.size >= 2)
        assertEquals(listOf(1, 2), parsedEmptyPoints.referencedPages)

        // 4. Verify Bengali Markdown fallback summary parsing
        val bengaliMarkdown = """
            **সংক্ষিপ্ত সারসংক্ষেপ:**
            এই প্রতিবেদনে সৌর শক্তির ব্যবহার এবং পরিবেশ সুরক্ষা নিয়ে আলোচনা করা হয়েছে [পৃষ্ঠা ২]।
            
            **মূল পয়েন্টসমূহ:**
            - নবায়নযোগ্য শক্তির ব্যবহার ৩৫% বৃদ্ধি পেয়েছে [পৃষ্ঠা ২]।
            - গ্রামীণ এলাকায় নতুন সৌর প্যানেল স্থাপন করা হয়েছে [পৃষ্ঠা ৪]।
        """.trimIndent()
        val parsedBnMarkdown = com.example.ai.PdfAiAssistantEngine.parseSummaryResponse(
            rawResponse = bengaliMarkdown,
            pageRangeDescription = "Pages 1-4",
            outputLanguage = com.example.ai.AiOutputLanguage.BENGALI,
            fallbackPages = listOf(1, 2, 3, 4)
        )
        assertEquals(true, parsedBnMarkdown.shortSummary.contains("সৌর শক্তির ব্যবহার"))
        assertEquals(2, parsedBnMarkdown.keyPoints.size)
        assertEquals(listOf(2, 4), parsedBnMarkdown.referencedPages)

        // 5. Verify large document page sampling and PUA unmapped glyph filtering
        val hundredPages = (0 until 100).toList()
        val sampledPages = com.example.ai.PdfAiAssistantEngine.samplePageIndicesForExtraction(hundredPages, maxPages = 24)
        assertEquals(24, sampledPages.size)
        assertEquals(true, sampledPages.containsAll(listOf(0, 1, 2, 98, 99)))

        val puaGarbled = "\uE001\uE002\uF020\uFFFD   "
        val cleanedGarbled = com.example.ai.PdfAiAssistantEngine.cleanReadablePdfText(puaGarbled)
        assertEquals(0, com.example.ai.PdfAiAssistantEngine.countReadableCharacters(cleanedGarbled))

        // 6. Verify PdfBoxMemoryUtil loads file:// URIs directly and cleans up scratch files
        val tempPdf = File(context.cacheDir, "file_uri_summary_test.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            doc.save(tempPdf)
        }
        val fileUri = android.net.Uri.fromFile(tempPdf)
        val loadedDoc = com.example.pdf.PdfBoxMemoryUtil.loadDocument(context, pdfFile = null, uri = fileUri)
        assertNotNull(loadedDoc)
        assertEquals(2, loadedDoc!!.numberOfPages)
        loadedDoc.close()
        tempPdf.delete()

        val scratchDir = com.example.pdf.PdfBoxMemoryUtil.getScratchDir(context)
        val dummyScratch = File(scratchDir, "temp_scratch.tmp").apply { writeText("scratch") }
        assertEquals(true, dummyScratch.exists())
        com.example.pdf.PdfBoxMemoryUtil.cleanupScratchFiles(context)
        assertEquals(false, dummyScratch.exists())

        // 7. Verify default themeMode is LIGHT and toggleLightDarkTheme + trimDeviceMemory work cleanly
        assertEquals(AppThemeMode.LIGHT, settingsManager.settings.value.themeMode)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.example.data.PdfRepository(db.recentPdfDao(), db.bookmarkDao())
        val viewModel = com.example.ui.PdfViewModel(app, repo, settingsManager)
        viewModel.toggleLightDarkTheme()
        assertEquals(AppThemeMode.DARK, settingsManager.settings.value.themeMode)
        viewModel.toggleLightDarkTheme()
        assertEquals(AppThemeMode.LIGHT, settingsManager.settings.value.themeMode)
        viewModel.trimDeviceMemory()
    }
}

