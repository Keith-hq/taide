package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

/** A separate, read-only editor tab; ordinary PDF/PPTX file associations stay available. */
final class SourcePreviewFile extends LightVirtualFile {
    final VirtualFile source;
    final int initialPage;

    SourcePreviewFile(VirtualFile source, int page) {
        super(source.getName() + " (TAIDE)", PlainTextFileType.INSTANCE, "");
        this.source = source;
        this.initialPage = page;
        setWritable(false);
    }
}
