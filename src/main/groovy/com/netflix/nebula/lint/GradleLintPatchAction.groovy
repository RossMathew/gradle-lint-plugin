        fixes.groupBy { it.affectedFile }.each { file, fileFixes ->

            for (f in fileFixes.sort { f1, f2 -> f1.from() <=> f2.from() ?: f1.to() <=> f2.to() ?: f1.changes() <=> f2.changes() }) {
                    curPatch = [f]
            def newlineAtEndOfOriginal = file.text.empty ? false : file.text[-1] == '\n'
            def lines = [''] + file.readLines() // the extra empty line is so we don't have to do a bunch of zero-based conversions for line arithmetic
                    if(j == 0) {
                } else if (fix instanceof GradleLintInsertAfter && fix.afterLine == lines.size() - 1 && !newlineAtEndOfOriginal && !file.text.empty) {
                    if (lastFix && lastLineOfContext == lines.size() && file.text[-1] != '\n') {
            def path = project.rootDir.toPath().relativize(file.toPath()).toString()

            combinedPatch += """\
                --- a/$path
                +++ b/$path
                @@ -${file.text.empty ? 0 : firstLineOfContext},$beforeLineCount +${afterLineCount == 0 ? 0 : firstLineOfContext},$afterLineCount @@
            """.stripIndent() + patch.join('\n')