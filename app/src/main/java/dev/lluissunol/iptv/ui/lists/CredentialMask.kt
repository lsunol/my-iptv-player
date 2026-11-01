package dev.lluissunol.iptv.ui.lists

private const val BULLET = "•"

/**
 * `username=`, `password=`, `token=`, `auth=`, `key=`, `user=`, `pass=`, `u=`
 * and `p=` values in a query string. The leading group keeps the separator
 * (`?`, `&`, `;` or whitespace) plus the parameter name itself visible.
 */
private val credentialParamPattern = Regex(
    """(?i)(^|[?&;\s])(username|password|token|auth|key|user|pass|u|p)=([^&\s]+)"""
)

/** `http://usuario:contraseña@host` userinfo, which carries credentials too. */
private val userInfoPattern = Regex("""(?i)//([^/\s:@]+):([^/\s@]+)@""")

/**
 * Masks the credential values of a URL (query parameters and `user:password@`
 * userinfo) so a pasted list URL can be shown without exposing what it contains.
 *
 * The replacement has exactly the same length as the matched text, which is what
 * lets the UI use it as a `VisualTransformation` with an identity offset mapping
 * (the cursor stays where the user put it).
 */
internal fun maskCredentials(text: String): String = maskUserInfo(maskParamValues(text))

private fun maskParamValues(text: String): String = credentialParamPattern.replace(text) { match ->
    val separator = match.groupValues[1]
    val name = match.groupValues[2]
    val value = match.groupValues[3]
    separator + name + "=" + BULLET.repeat(value.length)
}

private fun maskUserInfo(text: String): String = userInfoPattern.replace(text) { match ->
    val user = match.groupValues[1]
    val password = match.groupValues[2]
    "//" + BULLET.repeat(user.length) + ":" + BULLET.repeat(password.length) + "@"
}
