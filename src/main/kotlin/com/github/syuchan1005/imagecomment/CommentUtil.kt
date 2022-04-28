package com.github.syuchan1005.imagecomment

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import java.io.IOException
import java.util.regex.Pattern

object CommentUtil {
    private val imageCommentPattern = Pattern.compile("!\\[(.*?)]\\((.*?)\\)")

    fun getMatchData(text: String): List<MatchData> {
        val matcher = imageCommentPattern.matcher(text)
        val matches: MutableList<MatchData> = ArrayList()
        while (matcher.find()) {
            matches.add(MatchData(matcher.group(), matcher.start()))
        }
        return matches
    }

    fun getImageByteArray(element: PsiElement, path: String): ByteArray? {
        val currentFile = element.containingFile.virtualFile ?: return null
        val folder = if (path.startsWith("/")) {
            val projectFileIndex = ProjectFileIndex.getInstance(element.project)
            projectFileIndex.getContentRootForFile(currentFile)
        } else {
            currentFile.parent
        }
        val file = folder?.findFileByRelativePath(path) ?: return null
        return try {
            file.contentsToByteArray()
        } catch (e: IOException) {
            null
        }
    }

    class MatchData(val text: String, val startOffset: Int) {
        val alt: String = text.substring(2, text.indexOf('(') - 1)
        val path: String = text.substring(text.indexOf('(') + 1, text.length - 1)
    }
}
