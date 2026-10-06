package cn.ptdocs.librechatapp.web

import android.webkit.WebView

object RenameFocusGuard {

    private const val SCRIPT = """
(function () {
  if (window.__lcRenameFocusGuard) return;
  window.__lcRenameFocusGuard = true;
  var STEAL_MS = 300;
  var MAX_REFOCUS = 3;
  var focusTimes = new WeakMap();
  var refocusCounts = new WeakMap();

  function isEditable(el) {
    if (!el || !el.tagName) return false;
    if (el.isContentEditable) return true;
    return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA';
  }

  function isGuardedInput(el) {
    if (!el || el.tagName !== 'INPUT') return false;
    var ty = String(el.type || 'text').toLowerCase();
    return ty === 'text' || ty === 'search';
  }

  document.addEventListener('focusin', function (e) {
    var el = e.target;
    if (!isGuardedInput(el)) return;
    focusTimes.set(el, Date.now());
    refocusCounts.set(el, 0);
  }, true);

  document.addEventListener('focusout', function (e) {
    var el = e.target;
    if (!isGuardedInput(el)) return;
    var t0 = focusTimes.get(el);
    if (t0 == null) return;
    if (Date.now() - t0 > STEAL_MS) return;
    var next = e.relatedTarget || document.activeElement;
    if (isEditable(next)) return;
    var count = refocusCounts.get(el) || 0;
    if (count >= MAX_REFOCUS) return;
    refocusCounts.set(el, count + 1);
    setTimeout(function () {
      if (document.contains(el)) {
        try {
          el.focus({ preventScroll: true });
        } catch (err) {
          try { el.focus(); } catch (err2) {}
        }
      }
    }, 0);
  }, true);
})();
"""

    fun inject(webView: WebView) {
        webView.evaluateJavascript(SCRIPT, null)
    }
}
