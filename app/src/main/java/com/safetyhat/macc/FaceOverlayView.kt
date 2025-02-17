package com.safetyhat.macc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // Lista di rettangoli dei volti rilevati
    private var faceBoundingBoxes: List<RectF> = emptyList()
    private val hatBitmap: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.hard_hat)
    private val reusableRect = RectF()

    fun setFaces(faces: List<RectF>) {
        faceBoundingBoxes = faces
        invalidate() // Forza il ridisegno
    }

    /*override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas ?: return

        for (faceRect in faceBoundingBoxes) {
            canvas.drawRect(faceRect, paint)
        }
    }*/

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (faceRect in faceBoundingBoxes) {
            // Disegna il bounding box (opzionale)
            //canvas.drawRect(faceRect, paint)

            // Calcola le dimensioni e la posizione del cappello
            val hatWidth = faceRect.width()
            val hatHeight = hatBitmap.height * (hatWidth / hatBitmap.width)

            reusableRect.set(
                faceRect.left,
                faceRect.top - hatHeight + 50f, // Cappello più in basso di 20px
                faceRect.right,
                faceRect.top + 50f
            )


            // Disegna il cappello
            canvas.drawBitmap(hatBitmap, null, reusableRect, null)
        }
    }


}
