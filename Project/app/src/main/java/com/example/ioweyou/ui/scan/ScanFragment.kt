package com.example.ioweyou.ui.scan

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ioweyou.databinding.FragmentScanBinding
import com.example.ioweyou.model.LineItem
import com.example.ioweyou.ui.MainViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private val lineItems = mutableListOf<LineItem>()
    private lateinit var lineItemAdapter: LineItemAdapter
    private val args: ScanFragmentArgs by navArgs()
    private var editExpenseId: String = ""

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lineItemAdapter = LineItemAdapter(
            lineItems,
            getFriends = { viewModel.friends.value ?: emptyList() },
            onChanged = { updateSubtotal() }
        )

        binding.rvLineItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLineItems.adapter = lineItemAdapter

        editExpenseId = args.editExpenseId
        if (editExpenseId.isNotEmpty()) {
            val expense = viewModel.expenses.value?.find { it.id == editExpenseId }
            if (expense != null) {
                binding.etExpenseTitle.setText(expense.title)
                val parsedItems = expense.getParsedLineItems()
                lineItems.clear()
                lineItems.addAll(parsedItems)
                if (lineItems.isEmpty()) lineItems.add(LineItem(name = "", price = 0.0))
                lineItemAdapter.notifyDataSetChanged()
                updateSubtotal()
            }
            showReviewMode()
        }

        val manualEntry = arguments?.getBoolean("manualEntry", false) ?: false
        if (editExpenseId.isEmpty() && manualEntry) {
            lineItems.add(LineItem(name = "", price = 0.0))
            lineItemAdapter.notifyDataSetChanged()
            showReviewMode()
        } else if (editExpenseId.isEmpty()) {
            showCameraMode()
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                requestPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.btnRetake.setOnClickListener {
            lineItems.clear()
            lineItemAdapter.notifyDataSetChanged()
            binding.etTax.setText("")
            updateSubtotal()
            binding.btnCapture.isEnabled = true
            showCameraMode()
        }

        binding.btnAddItem.setOnClickListener {
            lineItems.add(LineItem(name = "", price = 0.0))
            lineItemAdapter.notifyItemInserted(lineItems.size - 1)
        }

        binding.etTax.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateSubtotal() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnSave.setOnClickListener { saveExpense() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false

        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    runOcr(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(requireContext(), "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun runOcr(bitmap: android.graphics.Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                recognizer.close()
                result.textBlocks.forEachIndexed { bi, block ->
                    block.lines.forEachIndexed { li, line ->
                        val midY = line.boundingBox?.let { (it.top + it.bottom) / 2 } ?: -1
                        Log.d(TAG, "OCR [$bi][$li] midY=$midY: ${line.text}")
                    }
                }
                val (parsedItems, detectedTax) = parseOcrResult(result)
                lineItems.clear()
                lineItems.addAll(parsedItems)
                if (lineItems.isEmpty()) {
                    lineItems.add(LineItem(name = "", price = 0.0))
                }
                if (detectedTax > 0.0) {
                    binding.etTax.setText(String.format("%.2f", detectedTax))
                }
                lineItemAdapter.notifyDataSetChanged()
                updateSubtotal()
                showReviewMode()
            }
            .addOnFailureListener { e ->
                recognizer.close()
                Log.e(TAG, "OCR failed", e)
                binding.btnCapture.isEnabled = true
                Toast.makeText(requireContext(), "OCR failed, add items manually", Toast.LENGTH_SHORT).show()
                lineItems.clear()
                lineItems.add(LineItem(name = "", price = 0.0))
                lineItemAdapter.notifyDataSetChanged()
                updateSubtotal()
                showReviewMode()
            }
    }

    private fun parseOcrResult(result: Text): Pair<List<LineItem>, Double> {
        val priceRegex = Regex("""(?<!\w)[8Ss$]?([L\d]{1,3}\.\d{2})(?!\d)""")
        val skipKeywords = listOf(
            "total", "subtotal", "change", "cash", "credit", "debit",
            "visa", "mastercard", "memb", "thank", "savings", "coupon",
            "instant", "balance", "payment", "tender", "wholesale", "costco",
            "self-checkout", "self checkout", "receipt", "store", "ref#", "auth",
            "usd", "sales", "returned", "items total",
            "card", "chip", "approved", "reference", "date", "status"
        )

        data class OcrLine(val text: String, val mid: Int)

        val allLines = mutableListOf<OcrLine>()
        val noDecimalPrice = Regex("""^[8Ss$](\d{1,2})(\d{2})$""")
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                val ndMatch = noDecimalPrice.matchEntire(text)
                val normalized = if (ndMatch != null)
                    "\$${ndMatch.groupValues[1]}.${ndMatch.groupValues[2]}"
                else text
                allLines.add(OcrLine(normalized, (box.top + box.bottom) / 2))
            }
        }

        fun hasEnoughLetters(s: String) = s.count { it.isLetter() } >= 3

        val itemCodePattern = Regex("""^[\dA-Z]{4,}\s+[A-Z]""")
        val itemCodeLines = allLines.filter {
            itemCodePattern.containsMatchIn(it.text) &&
            !priceRegex.containsMatchIn(it.text) &&
            skipKeywords.none { kw -> it.text.lowercase().contains(kw) }
        }

        val items = mutableListOf<LineItem>()

        val priceLines = allLines.filter { line ->
            priceRegex.containsMatchIn(line.text) &&
            !line.text.trimEnd().endsWith("-") &&
            skipKeywords.none { line.text.lowercase().contains(it) }
        }

        val unmatchedPrices = mutableListOf<OcrLine>()

        for (pl in priceLines) {
            val match = priceRegex.find(pl.text) ?: continue
            val price = match.groupValues[1].replace('L', '1').toDoubleOrNull() ?: continue
            if (price <= 0 || price > 9999) continue

            val sameLine = pl.text.substring(0, match.range.first).trim().trimEnd('.', '-', ' ', '*')
            if (sameLine.length >= 2 && hasEnoughLetters(sameLine) &&
                skipKeywords.none { sameLine.lowercase().contains(it) }
            ) {
                items.add(LineItem(name = sameLine, price = price))
            } else {
                unmatchedPrices.add(pl)
            }
        }

        val remainingPrices = mutableListOf<OcrLine>()
        if (itemCodeLines.isNotEmpty()) {
            val availableItems = itemCodeLines.toMutableList()
            for (pl in unmatchedPrices) {
                val match = priceRegex.find(pl.text) ?: continue
                val price = match.groupValues[1].replace('L', '1').toDoubleOrNull() ?: continue
                if (price <= 0) continue

                if (availableItems.isNotEmpty()) {
                    val itemLine = availableItems.removeAt(0)
                    val parts = itemLine.text.split(Regex("\\s+"), 2)
                    val name = if (parts.size == 2 && parts[0].matches(Regex("[\\dA-Z-]+")))
                        parts[1].trim() else itemLine.text.trim()

                    if (skipKeywords.any { name.lowercase().contains(it) }) continue
                    if (name.length >= 2 && hasEnoughLetters(name)) {
                        items.add(LineItem(name = name, price = price))
                    }
                } else {
                    remainingPrices.add(pl)
                }
            }
        } else {
            remainingPrices.addAll(unmatchedPrices)
        }

        if (remainingPrices.isNotEmpty()) {
            if (itemCodeLines.isNotEmpty()) {
                val standaloneCandidates = allLines.filter { nl ->
                    !priceRegex.containsMatchIn(nl.text) &&
                    !itemCodePattern.containsMatchIn(nl.text) &&
                    skipKeywords.none { nl.text.lowercase().contains(it) } &&
                    nl.text.trim().all { it.isLetter() || it.isWhitespace() } &&
                    hasEnoughLetters(nl.text)
                }
                val usedMids = mutableSetOf<Int>()
                for (pl in remainingPrices) {
                    val match = priceRegex.find(pl.text) ?: continue
                    val price = match.groupValues[1].replace('L', '1').toDoubleOrNull() ?: continue
                    if (price <= 0 || price > 9999) continue
                    val best = standaloneCandidates
                        .filter { it.mid !in usedMids }
                        .minByOrNull { kotlin.math.abs(it.mid - pl.mid) }
                    if (best != null) {
                        usedMids.add(best.mid)
                        val name = best.text.trim()
                        if (skipKeywords.any { name.lowercase().contains(it) }) continue
                        if (name.length >= 2 && hasEnoughLetters(name))
                            items.add(LineItem(name = name, price = price))
                    }
                }
            } else {
                val longDigitRun = Regex("""\d{4,}""")
                val firstPriceIdx = remainingPrices.minOfOrNull { allLines.indexOf(it) } ?: 0
                val nameWithIdx = allLines.mapIndexedNotNull { idx, nl ->
                    if (idx >= firstPriceIdx &&
                        !priceRegex.containsMatchIn(nl.text) &&
                        skipKeywords.none { nl.text.lowercase().contains(it) } &&
                        !longDigitRun.containsMatchIn(nl.text) &&
                        nl.text.length >= 4 &&
                        hasEnoughLetters(nl.text)) Pair(idx, nl) else null
                }
                val usedAllLinesIdx = mutableSetOf<Int>()

                for (pl in remainingPrices) {
                    val priceIdx = allLines.indexOf(pl)
                    val windowStart = maxOf(0, priceIdx - 2)
                    val windowEnd = minOf(allLines.size, priceIdx + 3)
                    val nearLabel = allLines.subList(windowStart, windowEnd)
                        .any { nl -> skipKeywords.any { kw -> nl.text.lowercase().contains(kw) } }
                    if (nearLabel) continue

                    val match = priceRegex.find(pl.text) ?: continue
                    val price = match.groupValues[1].replace('L', '1').toDoubleOrNull() ?: continue
                    if (price <= 0 || price > 9999) continue

                    val best = nameWithIdx
                        .filter { (idx, _) -> idx !in usedAllLinesIdx }
                        .minByOrNull { (idx, _) -> kotlin.math.abs(idx - priceIdx) }
                    if (best != null) {
                        usedAllLinesIdx.add(best.first)
                        val name = best.second.text.trim()
                        if (skipKeywords.any { name.lowercase().contains(it) }) continue
                        if (name.length >= 4 && hasEnoughLetters(name))
                            items.add(LineItem(name = name, price = price))
                    }
                }
            }
        }

        val taxPriceRegex = Regex("""(?<!\w)[8Ss$]?([L\d0O]{1,3}\.\d{2})(?!\d)""")
        var detectedTax = 0.0
        val taxLineIdx = allLines.indexOfFirst { it.text.lowercase().contains("tax") }
        if (taxLineIdx >= 0) {
            val sameLineMatch = taxPriceRegex.find(allLines[taxLineIdx].text)
            if (sameLineMatch != null) {
                detectedTax = sameLineMatch.groupValues[1]
                    .replace('L', '1').replace('O', '0').toDoubleOrNull() ?: 0.0
            } else {
                val nearestPriceIdx = allLines.indices.filter { idx ->
                    idx != taxLineIdx &&
                    taxPriceRegex.containsMatchIn(allLines[idx].text) &&
                    skipKeywords.none { kw -> allLines[idx].text.lowercase().contains(kw) }
                }.minByOrNull { kotlin.math.abs(it - taxLineIdx) }
                if (nearestPriceIdx != null) {
                    val match = taxPriceRegex.find(allLines[nearestPriceIdx].text)
                    detectedTax = match?.groupValues?.get(1)
                        ?.replace('L', '1')?.replace('O', '0')?.toDoubleOrNull() ?: 0.0
                }
            }
        }

        return Pair(items, detectedTax)
    }

    private fun updateSubtotal() {
        val currency = java.text.NumberFormat.getCurrencyInstance()
        val subtotal = lineItems.sumOf { it.price }
        val tax = binding.etTax.text.toString().toDoubleOrNull() ?: 0.0
        binding.tvSubtotal.text = "Subtotal: ${currency.format(subtotal)}"
        binding.tvTotal.text = "Total: ${currency.format(subtotal + tax)}"
    }

    private fun showCameraMode() {
        binding.cameraPreview.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.VISIBLE
        binding.reviewLayout.visibility = View.GONE
    }

    private fun showReviewMode() {
        binding.cameraPreview.visibility = View.GONE
        binding.btnCapture.visibility = View.GONE
        binding.reviewLayout.visibility = View.VISIBLE
    }

    private fun saveExpense() {
        Log.d(TAG, "saveExpense called")
        val title = binding.etExpenseTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Enter an expense title first", Toast.LENGTH_SHORT).show()
            binding.etExpenseTitle.requestFocus()
            return
        }
        var items = lineItemAdapter.getItems().toMutableList()
        if (items.isEmpty() || items.all { it.price == 0.0 }) {
            Toast.makeText(requireContext(), "Add at least one item with a price", Toast.LENGTH_SHORT).show()
            return
        }

        val tax = binding.etTax.text.toString().toDoubleOrNull() ?: 0.0
        val participantUids = items.flatMap { it.assignees }.distinct()
        val friends = viewModel.friends.value ?: emptyList()
        val participants = friends.filter { it.uid in participantUids }

        if (tax > 0.0) {
            val allUids = (participantUids + viewModel.currentUser.uid).distinct()
            items.add(LineItem(name = "Tax / Tip", price = tax, assignees = allUids))
        }

        if (editExpenseId.isNotEmpty()) {
            viewModel.updateExpense(editExpenseId, title, items, participants)
            Toast.makeText(requireContext(), "Expense updated!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        } else {
            viewModel.saveExpense(title, items, participants)
            Toast.makeText(requireContext(), "Expense saved!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(
                com.example.ioweyou.R.id.homeFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(com.example.ioweyou.R.id.homeFragment, true)
                    .build()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ScanFragment"
    }
}
