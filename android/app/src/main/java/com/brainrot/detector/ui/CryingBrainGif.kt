package com.brainrot.detector.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.brainrot.detector.R

/**
 * The same animated GIF the overlay uses, so the user can see what they are
 * signing up for before they hand over any permissions.
 */
@Composable
fun CryingBrainGif(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                contentDescription = context.getString(R.string.crying_brain)
                runCatching {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(context.resources, R.raw.crying_brain)
                    )
                }.getOrNull()?.let { drawable ->
                    setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                }
            }
        },
    )
}
