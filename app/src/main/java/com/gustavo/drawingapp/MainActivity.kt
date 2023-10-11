package com.gustavo.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var drawingView :DrawingView?=null
    private var img_brush : ImageButton? = null
    private var img_undo:ImageButton? = null
    private var img_save:ImageButton?=null
    private var mImageButtonCurrentPaint:ImageButton? =null
    private var customProgressDialog :Dialog? = null

    val openGalleryLaucher :ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode== RESULT_OK && result.data!=null){
                val imageBackGround:ImageView = findViewById(R.id.img_background)

                imageBackGround.setImageURI(result.data?.data)
            }
        }

    val requestPermission :ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted){
                    Toast.makeText(this@MainActivity,"Permissão concedida com Sucesso",Toast.LENGTH_SHORT).show()

                    val pickedIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLaucher.launch(pickedIntent)

                }else{
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity,"Você negou as permissões",Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        img_brush = findViewById(R.id.imgButton_brush)
        img_brush?.setOnClickListener {
            showBrushSizeDialog()
        }

        img_undo = findViewById(R.id.imgButton_undo)
        img_undo?.setOnClickListener {
            drawingView?.onClickUndo()
        }

        img_save = findViewById(R.id.imgButton_save)
        img_save?.setOnClickListener {
            if(isReadStorageAllowed()){

                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView:FrameLayout = findViewById(R.id.fl_drawing_view)
                    val myBitmap :Bitmap = getBitmapFromView(flDrawingView)
                    saveBitmapFile(myBitmap)
                }
            }
        }


        val imgGallery :ImageButton = findViewById(R.id.imgButton_gallery)
        imgGallery.setOnClickListener {
            requestStoragePermission()
        }

    }

    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.imgButton_small)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.imgButton_medium)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.imgButton_large)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view : View){
        if(view != mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun showRationaleDialog(title:String, message:String){

        val builder :AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){ dialog,_->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Drawing App","Drawing app precisa acessar os Arquivos da Galeria")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun isReadStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED

    }

    private fun getBitmapFromView(view: View):Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable !=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap:Bitmap?):String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap !=null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() +
                    File.separator + "DrawingApp" + System.currentTimeMillis() /1000 + ".png"
                    )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,"Arquivo Salvo Com Sucesso :$result",Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity,"Algo Deu Errado Ao Salvar o arquivo !!",Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
        }
        return result
    }


    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog !=null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path , uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }


}