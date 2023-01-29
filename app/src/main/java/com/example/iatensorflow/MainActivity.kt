package com.example.iatensorflow

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import com.example.iatensorflow.databinding.ActivityMainBinding
import com.example.iatensorflow.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageview
        button = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {


            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }

        }

        buttonLoad.setOnClickListener {
            Toast.makeText(this, "Button Load", Toast.LENGTH_LONG).show()
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onResult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // redireciona para o google usando o nome científico como busca
        tvOutput.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${tvOutput.text}")
            )
            startActivity(intent)
        }

        // baixa a imagem usando longPress
        imageView.setOnLongClickListener {
            requestPermissionLaucher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }

    }

    // solicita a permissão do uso de câmera
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePicturePreview.launch(null)
            } else {
                Toast.makeText(this, "Permissão negada, tente novamente!", Toast.LENGTH_LONG).show()
            }
        }

    // abre câmera e tira foto
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                outputGenerator(bitmap)
            }
        }

    // pega imagem da galeria
    private val onResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i("TAG", "this is the result: ${result.data} ${result.resultCode}")
            onResultReceived(GALLERY_REQUEST_CODE, result)
        }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap =
                            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        // declarando variáveis do tensorFlow
        val birdsModel = BirdsModel.newInstance(this)
        // converte bitmap em imagem TensorFlow
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        // Creates inputs for reference.
        val tfimage = TensorImage.fromBitmap(newBitmap)

        // processa a imagem e organiza em ordem decrescente
        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }
        // resultado com alta probabilidade
        val highProbabilityOutput = outputs[0]

        // configurando texto de saída
        tvOutput.text = highProbabilityOutput.label
        Log.i("TAG", "outputgenerator: $highProbabilityOutput")
        // Releases model resources if no longer used.
//        birdsModel.close()
    }

    private val requestPermissionLaucher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                AlertDialog.Builder(this).setTitle("Baixar imagem?")
                    .setMessage("Você quer baixar essa imagem no seu dispositivo?")
                    .setPositiveButton("Sim") { _, _ ->
                        val drawable: BitmapDrawable = imageView.drawable as BitmapDrawable
                        val bitmap = drawable.bitmap
                        downloadImage(bitmap)
                    }
                    .setNegativeButton("Não!") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this, "por favor, permita baixar a imagem", Toast.LENGTH_LONG).show()
            }
        }

    private fun downloadImage(mBitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "Birds_Images" + System.currentTimeMillis() / 1000
            )
            put(MediaStore.Images.Media.MIME_TYPE, "images/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null) {
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)){
                        throw IOException("Couldn't save de bitmap")
                    } else {
                        Toast.makeText(applicationContext, "imagem salva", Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return null
    }
}