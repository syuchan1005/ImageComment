package com.github.syuchan1005.imagecomment

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import javax.imageio.ImageIO
import kotlin.math.max

class ImageLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (!element.node.elementType.toString().uppercase().endsWith("COMMENT")) {
            return
        }

        CommentUtil.getMatchData(element.text).forEach { matchData ->
            val bytes = CommentUtil.getImageByteArray(element, matchData.path) ?: return@forEach
            val bufferedImage = bytes.inputStream().use(ImageIO::read) ?: return@forEach
            val scaledImage = ImageUtil.scaleImage(
                bufferedImage,
                ICON_SIZE / max(bufferedImage.width, bufferedImage.height)
            )

            val builder = NavigationGutterIconBuilder.create(IconUtil.createImageIcon(scaledImage))
                .setTargets(element)
                .setTooltipText(matchData.alt)
            result.add(
                builder.createLineMarkerInfo(element)
            )
        }
    }

    private companion object {
        const val ICON_SIZE: Double = 16.0
    }
}
