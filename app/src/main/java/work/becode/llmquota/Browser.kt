package work.becode.llmquota

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens [url] in Chrome, which Claude's sign-in accepts; any other browser is the fallback. */
fun openInBrowser(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent(view).setPackage("com.android.chrome"))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(view)
    }
}

/** The provider's own page for the usage the card shows. */
fun usageUrl(provider: String): String = when (provider) {
    CODEX -> "https://chatgpt.com/codex/settings/usage"
    CLAUDE -> "https://claude.ai/settings/usage"
    else -> "https://platform.deepseek.com/usage"
}
