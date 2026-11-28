#!/usr/bin/env python3
"""
Static gate for the Fussoo VR Java sources.

The sandbox has no JDK and no Android SDK, so CI is the only real compiler. This script
catches the failure mode that actually shipped in v1: a reference to a member that does
not exist. It parses every file with javalang, indexes all declared members per type,
resolves inheritance, and then checks every `Type.member` / `object.member` reference
against that index.

Usage:  python3 tools/check_java.py android/app/src/main/java
Exit code 0 = clean, 1 = problems found.
"""
import os
import re
import sys

import javalang

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
errors = []
notes = []

# --------------------------------------------------------------- parse all files
files = {}
for dirpath, _, names in os.walk(ROOT):
    for n in sorted(names):
        if not n.endswith(".java"):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        try:
            files[path] = (src, javalang.parse.parse(src))
        except Exception as ex:
            errors.append("%s: PARSE FAIL: %s" % (path, ex))

print("parsed %d files" % len(files))


def walk_types(tree):
    """(parent, node) for every nested type declaration."""
    stack = [(None, t) for t in tree.types]
    while stack:
        parent, node = stack.pop()
        yield parent, node
        for b in (getattr(node, "body", None) or []):
            if isinstance(b, (javalang.tree.ClassDeclaration,
                              javalang.tree.EnumDeclaration,
                              javalang.tree.InterfaceDeclaration)):
                stack.append((node, b))


def type_name(t):
    """Simple name of a declared type, unwrapping Yt.Video -> Video and generics."""
    if t is None:
        return None
    sub = getattr(t, "sub_type", None)
    if sub is not None:
        return getattr(sub, "name", None)
    return getattr(t, "name", None)


def local_decls(method):
    out = []
    for _, n in method.filter(javalang.tree.LocalVariableDeclaration):
        t = type_name(n.type)
        if not t:
            continue
        for d in n.declarators:
            out.append((d.name, t))
    return out


# --------------------------------------------------------------- index members
# name -> {members:set, supers:set, file:str}
index = {}
for path, (src, tree) in files.items():
    for _, node in walk_types(tree):
        mem = set()
        for f in (getattr(node, "fields", None) or []):
            for d in f.declarators:
                mem.add(d.name)
        for m in (getattr(node, "methods", None) or []):
            mem.add(m.name)
        for c in (getattr(node, "constructors", None) or []):
            mem.add("<init>")
        eb = getattr(node, "body", None)
        for c in (getattr(eb, "constants", None) or []):
            mem.add(c.name)
        for b in (eb if isinstance(eb, list) else []):
            if isinstance(b, javalang.tree.EnumConstantDeclaration):
                mem.add(b.name)
            elif isinstance(b, javalang.tree.FieldDeclaration):
                for d in b.declarators:
                    mem.add(d.name)
            elif isinstance(b, (javalang.tree.MethodDeclaration,
                                javalang.tree.ConstantDeclaration)):
                mem.add(b.name)

        supers = set()
        ext = getattr(node, "extends", None)
        if isinstance(ext, list):
            for e in ext:
                supers.add(getattr(e, "name", ""))
        elif ext is not None:
            supers.add(getattr(ext, "name", ""))
        for i in (getattr(node, "implements", None) or []):
            supers.add(getattr(i, "name", ""))
        for j in (getattr(node, "extends", None) or []) if isinstance(
                getattr(node, "extends", None), list) else []:
            pass

        # nested types count as members of the enclosing type
        for b in (getattr(node, "body", None) or []):
            if isinstance(b, (javalang.tree.ClassDeclaration,
                              javalang.tree.EnumDeclaration,
                              javalang.tree.InterfaceDeclaration)):
                mem.add(b.name)
        slot = index.setdefault(node.name, {"members": set(), "supers": set(),
                                            "file": path})
        slot["members"] |= mem
        slot["supers"] |= {s for s in supers if s}

# interfaces of a class may extend several interfaces
for path, (src, tree) in files.items():
    for _, node in walk_types(tree):
        if isinstance(node, javalang.tree.InterfaceDeclaration):
            for e in (getattr(node, "extends", None) or []):
                index[node.name]["supers"].add(getattr(e, "name", ""))


def members_of(name, seen=None):
    seen = set() if seen is None else seen
    if name in seen or name not in index:
        return set()
    seen.add(name)
    out = set(index[name]["members"])
    for s in index[name]["supers"]:
        out |= members_of(s, seen)
    return out


def supers_of(name, seen=None):
    seen = set() if seen is None else seen
    if name in seen or name not in index:
        return set()
    seen.add(name)
    out = set(index[name]["supers"])
    for s in list(index[name]["supers"]):
        out |= supers_of(s, seen)
    return out


# Types we cannot see (framework / libraries). Resolved optimistically.
FRAMEWORK = set("""
R Build Log TextUtils System Math Integer Float Boolean String CharSequence List
ArrayList Map HashMap Set HashSet Collections Arrays Objects Locale Thread Handler
Looper Runnable UUID Uri Intent Bundle Activity Context View ViewGroup MotionEvent
GL10 GLES20 GLES11Ext EGL14 EGLDisplay EGLSurface EGLContext EGLConfig SurfaceTexture
Surface Bitmap BitmapFactory Canvas Paint Path RectF Rect Typeface Shader Color
PorterDuff PorterDuffXfermode LinearGradient RadialGradient DashPathEffect ByteBuffer
ByteOrder FloatBuffer ShortBuffer Matrix DisplayMetrics Configuration TypedValue
GradientDrawable WindowManager LayoutInflater SharedPreferences Editor ExoPlayer
MediaItem Player TrackSelectionParameters Vibrator SensorManager Sensor SensorEvent
SensorEventListener GLSurfaceView WebView WebSettings WebChromeClient WebViewClient
CookieManager MediaMetadataRetriever AudioManager PowerManager Window WindowInsets
Display Executor ExecutorService Executors Future Callable URL HttpURLConnection
InputStream OutputStream BufferedReader InputStreamReader ByteArrayOutputStream
ConnectivityManager NetworkInfo Spannable Toast Timer TimerTask AtomicInteger
AtomicBoolean ConcurrentHashMap CopyOnWriteArrayList StreamInfo VideoStream AudioStream
MediaFormat DeliveryMethod StreamType StreamInfoItem InfoItem SearchExtractor
KioskExtractor ListExtractor InfoItemsPage NewPipe ServiceList Localization
ContentCountry Image StreamingService Choreographer FrameCallback SurfaceHolder
MediaCodec PlaybackException Timeline PlaybackParameters MimeTypes Util
DefaultHttpDataSource HttpDataSource DefaultDataSource DataSource
ProgressiveMediaSource MergingMediaSource MediaSource SimpleCache ContextCompat
WindowCompat WindowInsetsControllerCompat DefaultTrackSelector TrackSelector C
HandlerThread Process Environment Base64 PackageManager ApplicationInfo ImageView
FrameLayout MediaPlayer SoundPool AssetManager AssetFileDescriptor MemoryFile
SharedPreferences OnClickListener Runnable IllegalArgumentException
IllegalStateException Exception Throwable InterruptedException IOException
NumberFormatException Math Float Double Long Short Byte Character StringBuilder
StringTokenizer Random Date SimpleDateFormat TimeZone GregorianCalendar
CountDownLatch Semaphore ReentrantLock Lock Condition MessageQueue Message
Linkify Pattern Matcher BitmapShader Shader TileMode Xfermode
""".split())

# --------------------------------------------------- field / local variable types
var_types = {}
for path, (src, tree) in files.items():
    for _, node in walk_types(tree):
        for f in (getattr(node, "fields", None) or []):
            t = type_name(f.type)
            if t:
                for d in f.declarators:
                    var_types.setdefault(d.name, t)
        for m in (getattr(node, "methods", None) or []) + \
                 (getattr(node, "constructors", None) or []):
            for p in (getattr(m, "parameters", None) or []):
                t = type_name(p.type)
                if t:
                    var_types.setdefault(p.name, t)
            for name, t in local_decls(m):
                var_types.setdefault(name, t)

# --------------------------------------------------------------- resolve refs
REF = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*([a-zA-Z_][A-Za-z0-9_]*)")
KEYWORDS = {"this", "super", "return", "new", "class", "public", "private", "static",
            "final", "void", "int", "float", "long", "double", "boolean", "byte",
            "char", "short", "if", "else", "for", "while", "switch", "case", "try",
            "catch", "throw", "throws", "import", "package", "synchronized"}

for path, (src, tree) in files.items():
    clean = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    clean = re.sub(r'//[^\n]*', '', clean)
    clean = re.sub(r'^(?:import|package)\s+[^;]+;', '', clean, flags=re.M)
    clean = re.sub(r'"(?:[^"\\]|\\.)*"', '""', clean)
    for mo in REF.finditer(clean):
        obj, mem = mo.group(1), mo.group(2)
        if obj in KEYWORDS:
            continue
        if obj in index:
            have = members_of(obj)
            if mem not in have:
                # could be a nested type or inherited from a framework superclass
                if supers_of(obj) & FRAMEWORK:
                    continue
                errors.append("%s: %s.%s — '%s' is not a member of %s"
                              % (path, obj, mem, mem, obj))
            continue
        vt = var_types.get(obj)
        if vt in index:
            have = members_of(vt)
            if mem not in have:
                if supers_of(vt) & FRAMEWORK:
                    continue
                errors.append("%s: %s(%s).%s — '%s' is not a member of %s"
                              % (path, obj, vt, mem, mem, vt))
        elif vt in FRAMEWORK or obj in FRAMEWORK:
            continue
        else:
            notes.append("%s: unknown receiver %s.%s" % (path, obj, mem))


# ------------------------------------------------ unqualified calls & inheritance
# A call like doThing() inside class A must resolve on A or on one of its supers. This
# catches the most expensive mistake in this codebase: calling a lifecycle hook that was
# never written, which only shows up at compile time in CI.
INHERITED_FREE = {
    "toString", "equals", "hashCode", "getClass", "notify", "notifyAll", "wait",
    "clone", "finalize", "getContext", "performHapticFeedback", "queueEvent",
    "requestRender", "setRenderer", "setRenderMode", "setEGLContextClientVersion",
    "setEGLConfigChooser", "setPreserveEGLContextOnPause", "setKeepScreenOn",
    "setFocusable", "setFocusableInTouchMode", "postDelayed", "post", "removeCallbacks",
    "runOnUiThread", "getResources", "getSystemService", "getWindow", "setContentView",
    "onBackPressed", "onCreate", "onResume", "onPause", "onDestroy", "onNewIntent",
    "onAttachedToWindow", "onDetachedFromWindow", "onTouchEvent", "onDrawFrame",
    "onSurfaceCreated", "onSurfaceChanged", "onFrameAvailable", "onSensorChanged",
    "onAccuracyChanged", "onPlaybackStateChanged", "onIsPlayingChanged",
    "onVideoSizeChanged", "onPlayerError", "getWidth", "getHeight", "invalidate",
    "getParent", "setAlpha", "setVisibility", "setLayoutParams", "addView", "findView",
}

for path, (src, tree) in files.items():
    for parent, node in walk_types(tree):
        if parent is not None:
            continue  # nested types are checked with their enclosing type
        # All types in one compilation unit can see each other, so pool their methods.
        own = set()
        for _, t in walk_types(tree):
            own |= {m.name for m in (getattr(t, "methods", None) or [])}
            for b in (getattr(t, "body", None) or []):
                if isinstance(b, javalang.tree.MethodDeclaration):
                    own.add(b.name)
        inherited = set()
        for sname in index.get(node.name, {}).get("supers", set()):
            inherited |= members_of(sname)
        if index.get(node.name, {}).get("supers", set()) & FRAMEWORK:
            continue
        seen = set()
        for m in (getattr(node, "methods", None) or []):
            for _, inv in m.filter(javalang.tree.MethodInvocation):
                if inv.qualifier is not None:
                    continue
                nm = inv.member
                if nm in own or nm in inherited or nm in INHERITED_FREE or nm in seen:
                    continue
                seen.add(nm)
                # Only report names that the project declares somewhere: those are the
                # copy-paste / renamed-hook mistakes. Anything else is a framework call
                # this checker cannot see, and CI is the authority there.
                if not any(nm in v["members"] for v in index.values()):
                    continue
                # Chain receivers (`a.b().c()`) lose their qualifier in javalang's AST.
                chain = re.compile(r"[.)\]\w]\s*\.\s*" + re.escape(nm) + r"\s*\(")
                if chain.search(src):
                    continue
                errors.append("%s: %s.%s() — called but not declared (or inherited)"
                              % (path, node.name, nm))

print("\n--- errors (%d) ---" % len(errors))
for e in errors:
    print("  " + e)

if notes:
    uniq = sorted(set(notes))
    print("\n--- unresolved receivers (%d unique, informational) ---" % len(uniq))
    for n in uniq[:60]:
        print("  " + n)

sys.exit(1 if errors else 0)
