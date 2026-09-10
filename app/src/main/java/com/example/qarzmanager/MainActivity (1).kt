
package com.example.qarzmanager

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64

data class Debt(
    var id: Long,
    var person: String,
    var phone: String,
    var amount: Double,
    var paid: Double,
    var type: Int,                 // 0 = I owe, 1 = owed to me
    var date: Long,
    var dueDate: Long,
    var note: String
) {
    val remaining get() = (amount - paid).coerceAtLeast(0.0)
}

data class Payment(var id: Long, var debtId: Long, var amount: Double, var date: Long, var note: String)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("qarz_manager_final", MODE_PRIVATE) }
    private val debts = mutableListOf<Debt>()
    private val payments = mutableListOf<Payment>()
    private var lang = 0
    private var dark = false
    private var pin = ""
    private var unlocked = true
    private lateinit var root: LinearLayout

    private val blue = Color.rgb(49, 94, 247)
    private val green = Color.rgb(22, 138, 90)
    private val red = Color.rgb(214, 69, 69)
    private val bgLight = Color.rgb(246, 247, 251)
    private val bgDark = Color.rgb(18, 20, 25)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        load()
        if (pin.isNotBlank() && prefs.getBoolean("lock_enabled", true)) {
            unlocked = false
            pinScreen()
        } else home()
    }

    private fun t(ps:String, fa:String, en:String) = when(lang) { 0 -> ps; 1 -> fa; else -> en }

    private fun money(v: Double) = "؋ " + String.format(Locale.US, "%,.2f", v)

    private fun date(ms: Long): String =
        if (ms <= 0) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    private fun enc(s:String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun dec(s:String) = try { String(Base64.decode(s, Base64.NO_WRAP), Charsets.UTF_8) } catch(_:Exception) { "" }

    private fun load() {
        lang = prefs.getInt("lang", 0)
        dark = prefs.getBoolean("dark", false)
        pin = prefs.getString("pin", "") ?: ""
        debts.clear(); payments.clear()

        prefs.getString("debts", "")?.takeIf { it.isNotBlank() }?.split(";;")?.forEach { row ->
            val a=row.split("|")
            if(a.size >= 9) try {
                debts.add(Debt(a[0].toLong(),dec(a[1]),dec(a[2]),a[3].toDouble(),a[4].toDouble(),
                    a[5].toInt(),a[6].toLong(),a[7].toLong(),dec(a[8])))
            } catch(_:Exception){}
        }
        prefs.getString("payments", "")?.takeIf { it.isNotBlank() }?.split(";;")?.forEach { row ->
            val a=row.split("|")
            if(a.size >= 5) try { payments.add(Payment(a[0].toLong(),a[1].toLong(),a[2].toDouble(),a[3].toLong(),dec(a[4]))) } catch(_:Exception){}
        }
    }

    private fun save() {
        prefs.edit()
            .putInt("lang",lang)
            .putBoolean("dark",dark)
            .putString("pin",pin)
            .putString("debts",debts.joinToString(";;") {
                "${it.id}|${enc(it.person)}|${enc(it.phone)}|${it.amount}|${it.paid}|${it.type}|${it.date}|${it.dueDate}|${enc(it.note)}"
            })
            .putString("payments",payments.joinToString(";;") {
                "${it.id}|${it.debtId}|${it.amount}|${it.date}|${enc(it.note)}"
            }).apply()
    }

    private fun dp(v:Int):Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color:Int, radius:Int=16, stroke:Int=0, strokeColor:Int=Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius=dp(radius).toFloat()
            if(stroke>0) setStroke(dp(stroke),strokeColor)
        }

    private fun page(title:String, back:Boolean=true) {
        val scroll=ScrollView(this).apply {
            fillViewport=true
            setBackgroundColor(if(dark) bgDark else bgLight)
        }
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(10),dp(16),dp(24))
            setBackgroundColor(if(dark) bgDark else bgLight)
            layoutDirection=if(lang==0 || lang==1) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }

        val bar=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,0,0,dp(8))
        }
        if(back) {
            val b=TextView(this).apply {
                text="‹"
                textSize=34f
                gravity=Gravity.CENTER
                setTextColor(if(dark) Color.WHITE else Color.rgb(35,40,50))
                background=rounded(if(dark) Color.rgb(42,46,56) else Color.WHITE,14)
                elevation=dp(2).toFloat()
                setOnClickListener{home()}
                contentDescription="Back"
            }
            bar.addView(b, LinearLayout.LayoutParams(dp(48),dp(48)).apply{setMargins(0,0,dp(10),0)})
        }
        val h=TextView(this).apply {
            text=title
            textSize=23f
            setTextColor(if(dark) Color.WHITE else Color.rgb(20,25,36))
            setTypeface(null,1)
            gravity=if(lang==0 || lang==1) Gravity.CENTER_VERTICAL or Gravity.RIGHT else Gravity.CENTER_VERTICAL or Gravity.LEFT
            maxLines=2
        }
        bar.addView(h, LinearLayout.LayoutParams(0,dp(56),1f))
        root.addView(bar)
        scroll.addView(root,ScrollView.LayoutParams(-1,-2))
        setContentView(scroll)
    }

    private fun txt(s:String, size:Float=15f): TextView = TextView(this).apply {
        text=s
        textSize=size
        setTextColor(if(dark) Color.WHITE else Color.rgb(30,35,45))
        setPadding(dp(2),dp(4),dp(2),dp(4))
        gravity=if(lang==0 || lang==1) Gravity.RIGHT else Gravity.LEFT
    }

    private fun sectionTitle(s:String) {
        val v=TextView(this).apply {
            text=s
            textSize=17f
            setTypeface(null,1)
            setTextColor(if(dark) Color.WHITE else Color.rgb(30,35,45))
            gravity=if(lang==0 || lang==1) Gravity.RIGHT else Gravity.LEFT
            setPadding(dp(4),dp(12),dp(4),dp(4))
        }
        root.addView(v,LinearLayout.LayoutParams(-1,dp(46)))
    }

    private fun button(text:String, action:()->Unit, primary:Boolean=false) {
        val bg=if(primary) blue else if(dark) Color.rgb(42,46,56) else Color.WHITE
        val fg=if(primary) Color.WHITE else if(dark) Color.WHITE else Color.rgb(30,35,45)
        val b=Button(this).apply {
            this.text=text
            textSize=15.5f
            isAllCaps=false
            setTextColor(fg)
            gravity=Gravity.CENTER
            minHeight=dp(56)
            setPadding(dp(14),dp(8),dp(14),dp(8))
            background=rounded(bg,14,if(primary) 0 else 1,if(dark) Color.rgb(65,70,82) else Color.rgb(225,228,235))
            elevation=dp(2).toFloat()
            setOnClickListener{action()}
        }
        root.addView(b, LinearLayout.LayoutParams(-1,dp(58)).apply{setMargins(0,dp(5),0,dp(5))})
    }

    private fun card(text:String, action:(()->Unit)?=null) {
        val tv=TextView(this).apply {
            this.text=text
            textSize=15f
            setPadding(dp(18),dp(16),dp(18),dp(16))
            setTextColor(if(dark) Color.WHITE else Color.rgb(25,30,40))
            gravity=if(lang==0 || lang==1) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL
            background=rounded(if(dark) Color.rgb(31,35,43) else Color.WHITE,16,1,
                if(dark) Color.rgb(55,60,72) else Color.rgb(228,230,236))
            elevation=dp(2).toFloat()
            if(action!=null) {
                isClickable=true
                setOnClickListener{action()}
            }
        }
        root.addView(tv, LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,dp(5),0,dp(5))})
    }

    private fun home() {
        // Professional dashboard: compact header, summary card, stat cards,
        // quick actions, recent accounts and a persistent-looking navigation row.
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) bgDark else bgLight)
            layoutDirection = if (lang == 0 || lang == 1) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }

        val scroll = ScrollView(this).apply {
            fillViewport = true
            setBackgroundColor(if (dark) bgDark else bgLight)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(18))
            setBackgroundColor(if (dark) bgDark else bgLight)
            layoutDirection = if (lang == 0 || lang == 1) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }

        fun titleView(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = if (lang == 0 || lang == 1) Gravity.RIGHT or Gravity.CENTER_VERTICAL else Gravity.LEFT or Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, 1)
        }

        // Header
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.WHITE else Color.rgb(30, 55, 95))
            background = rounded(if (dark) Color.rgb(42,46,56) else Color.WHITE, 14, 1,
                if (dark) Color.rgb(65,70,82) else Color.rgb(225,228,235))
            setOnClickListener { settings() }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        val headTitle = titleView(t("د پورونو مدیریت", "مدیریت قرض‌ها", "Debt Manager"), 21f,
            if (dark) Color.WHITE else Color.rgb(20, 45, 80), true)
        header.addView(headTitle, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            setMargins(dp(10), 0, dp(10), 0)
        })
        val bell = TextView(this).apply {
            text = "🔔"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.WHITE else Color.rgb(30,55,95))
            background = rounded(if (dark) Color.rgb(42,46,56) else Color.WHITE, 14, 1,
                if (dark) Color.rgb(65,70,82) else Color.rgb(225,228,235))
            setOnClickListener {
                val due = debts.filter { it.remaining > 0 && it.dueDate > 0 && it.dueDate <= System.currentTimeMillis() }
                if (due.isEmpty()) toast(t("اوس د ورکړې حساب نشته.", "فعلاً حساب سررسید شده ندارید.", "No accounts are due now."))
                else accounts(due)
            }
        }
        header.addView(bell, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val now = Calendar.getInstance()
        val mine = debts.filter { it.type == 0 }.sumOf { it.remaining }
        val owed = debts.filter { it.type == 1 }.sumOf { it.remaining }
        val total = mine + owed
        val today = debts.filter { sameDay(it.date, now) }
        val month = debts.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }

        // Main summary / hero card
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = rounded(blue, 22)
            elevation = dp(5).toFloat()
        }
        val heroTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val wallet = TextView(this).apply {
            text = "▣"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(45,255,255,255), 16)
        }
        heroTop.addView(wallet, LinearLayout.LayoutParams(dp(52), dp(52)))
        val heroLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroLabels.addView(titleView(t("پاتې ټول پور", "مجموع باقی قرض", "Total Remaining"), 14f, Color.argb(225,255,255,255), false))
        heroLabels.addView(titleView(money(total), 28f, Color.WHITE, true))
        heroTop.addView(heroLabels, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(12),0,0,0) })
        hero.addView(heroTop)
        val heroBottom = titleView(
            t("ټول حسابونه: ${debts.size}", "تعداد حساب‌ها: ${debts.size}", "Accounts: ${debts.size}"),
            13f, Color.argb(215,255,255,255), false)
        heroBottom.setPadding(0, dp(10), 0, 0)
        hero.addView(heroBottom)
        root.addView(hero, LinearLayout.LayoutParams(-1, dp(132)).apply { setMargins(0,0,0,dp(10)) })

        sectionTitle(t("لنډیز", "خلاصه", "Overview"))

        // 2x2 stats grid
        fun statCard(label: String, value: String, icon: String, color: Int): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(if (dark) Color.rgb(31,35,43) else Color.WHITE, 18, 1,
                    if (dark) Color.rgb(55,60,72) else Color.rgb(228,230,236))
                elevation = dp(2).toFloat()
                val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                val ic = TextView(this@MainActivity).apply {
                    text = icon
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(color)
                    background = rounded(Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)), 12)
                }
                top.addView(ic, LinearLayout.LayoutParams(dp(38),dp(38)))
                top.addView(titleView(label, 12.5f, if (dark) Color.LTGRAY else Color.rgb(95,100,110), false),
                    LinearLayout.LayoutParams(0,dp(38),1f).apply { setMargins(dp(8),0,0,0) })
                addView(top)
                addView(titleView(value, 18f, if (dark) Color.WHITE else Color.rgb(25,35,55), true))
            }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(statCard(t("زما باندې پورونه","قرض من","I Owe"), money(mine), "↗", red),
            LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(0,0,dp(5),0) })
        row1.addView(statCard(t("پر ما باندې پورونه","طلب از من","Owed to Me"), money(owed), "↙", green),
            LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(dp(5),0,0,0) })
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(8),0,0) }
        row2.addView(statCard(t("د نن ورځې حساب","حساب امروز","Today"), "${today.size}", "▣", blue),
            LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(0,0,dp(5),0) })
        row2.addView(statCard(t("د دې میاشتې حساب","حساب ماه","This Month"), "${month.size}", "◷", blue),
            LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(dp(5),0,0,0) })
        root.addView(row2)

        sectionTitle(t("چټک عملیات", "عملیات سریع", "Quick Actions"))

        // Primary actions as a 2-column grid, like a modern finance app.
        fun actionCard(label: String, icon: String, color: Int, click: () -> Unit): TextView {
            return TextView(this).apply {
                text = "$icon\n$label"
                textSize = 14f
                setTypeface(null, 1)
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = rounded(if (dark) Color.rgb(31,35,43) else Color.WHITE, 18, 1,
                    if (dark) Color.rgb(55,60,72) else Color.rgb(228,230,236))
                elevation = dp(2).toFloat()
                minHeight = dp(82)
                setOnClickListener { click() }
            }
        }
        val actions1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions1.addView(actionCard(t("نوی پور اضافه کړه","ثبت قرض جدید","Add New Debt"), "+", blue) { addDebt(null) },
            LinearLayout.LayoutParams(0,dp(88),1f).apply { setMargins(0,0,dp(5),0) })
        actions1.addView(actionCard(t("تادیه ثبت کړه","ثبت پرداخت","Add Payment"), "✓", green) { choosePayment() },
            LinearLayout.LayoutParams(0,dp(88),1f).apply { setMargins(dp(5),0,0,0) })
        root.addView(actions1)

        val actions2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(8),0,0) }
        actions2.addView(actionCard(t("حسابونه","حساب‌ها","Accounts"), "◉", blue) { accounts(debts) },
            LinearLayout.LayoutParams(0,dp(82),1f).apply { setMargins(0,0,dp(5),0) })
        actions2.addView(actionCard(t("راپورونه","گزارش‌ها","Reports"), "▥", blue) { reports() },
            LinearLayout.LayoutParams(0,dp(82),1f).apply { setMargins(dp(5),0,0,0) })
        root.addView(actions2)

        sectionTitle(t("وروستي حسابونه", "حساب‌های اخیر", "Recent Accounts"))
        val recent = debts.sortedByDescending { it.date }.take(5)
        if (recent.isEmpty()) {
            val empty = titleView(t("تر اوسه کوم پور نه دی ثبت شوی.","هنوز قرضی ثبت نشده است.","No debts have been added yet."), 15f,
                if (dark) Color.LTGRAY else Color.rgb(100,105,115), false)
            empty.gravity = Gravity.CENTER
            empty.background = rounded(if (dark) Color.rgb(31,35,43) else Color.WHITE, 18, 1,
                if (dark) Color.rgb(55,60,72) else Color.rgb(228,230,236))
            root.addView(empty, LinearLayout.LayoutParams(-1,dp(78)))
        } else {
            recent.forEach { d ->
                recentDebtCard(d)
            }
        }

        // Secondary tools
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(10),0,0) }
        tools.addView(actionCard(t("لټون","جستجو","Search"), "⌕", blue) { search() },
            LinearLayout.LayoutParams(0,dp(72),1f).apply { setMargins(0,0,dp(5),0) })
        tools.addView(actionCard(t("تنظیمات","تنظیمات","Settings"), "⚙", blue) { settings() },
            LinearLayout.LayoutParams(0,dp(72),1f).apply { setMargins(dp(5),0,0,0) })
        root.addView(tools)

        root.addView(titleView(t("Qarz Manager • آفلاین", "Qarz Manager • آفلاین", "Qarz Manager • Offline"),
            11.5f, if (dark) Color.GRAY else Color.rgb(140,145,155), false).apply {
                gravity = Gravity.CENTER
                setPadding(0,dp(14),0,dp(4))
            })

        scroll.addView(root, ScrollView.LayoutParams(-1,-2))
        screen.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6),dp(6),dp(6),dp(6))
            background = if (dark) rounded(Color.rgb(25,28,34), 18) else rounded(Color.WHITE, 18)
            elevation = dp(8).toFloat()
        }
        fun navItem(icon:String, label:String, active:Boolean, click:()->Unit): TextView = TextView(this).apply {
            text = "$icon\n$label"
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(null, if(active) 1 else 0)
            setTextColor(if(active) blue else if(dark) Color.LTGRAY else Color.rgb(105,110,120))
            setOnClickListener { click() }
        }
        nav.addView(navItem("⌂", t("کور","خانه","Home"), true) { home() }, LinearLayout.LayoutParams(0,dp(62),1f))
        nav.addView(navItem("◉", t("حسابونه","حساب‌ها","Accounts"), false) { accounts(debts) }, LinearLayout.LayoutParams(0,dp(62),1f))
        nav.addView(navItem("▥", t("راپورونه","گزارش‌ها","Reports"), false) { reports() }, LinearLayout.LayoutParams(0,dp(62),1f))
        nav.addView(navItem("⚙", t("تنظیمات","تنظیمات","Settings"), false) { settings() }, LinearLayout.LayoutParams(0,dp(62),1f))
        screen.addView(nav, LinearLayout.LayoutParams(-1,dp(74)).apply { setMargins(dp(10),dp(6),dp(10),dp(10)) })

        setContentView(screen)
    }

    private fun recentDebtCard(d: Debt) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(10),dp(12),dp(10))
            background = rounded(if (dark) Color.rgb(31,35,43) else Color.WHITE, 16, 1,
                if (dark) Color.rgb(55,60,72) else Color.rgb(228,230,236))
            elevation = dp(1.5f.toInt()).toFloat()
            setOnClickListener { detail(d) }
        }
        val avatarColor = if (d.type == 0) Color.rgb(235,90,100) else Color.rgb(34,170,120)
        val avatar = TextView(this).apply {
            text = if (d.person.isBlank()) "?" else d.person.take(1).uppercase(Locale.getDefault())
            textSize = 17f
            setTypeface(null,1)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(avatarColor, 22)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(44),dp(44)))

        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(TextView(this@MainActivity).apply {
            text = d.person
            textSize = 15f
            setTypeface(null,1)
            setTextColor(if (dark) Color.WHITE else Color.rgb(25,35,50))
            gravity = if (lang == 0 || lang == 1) Gravity.RIGHT else Gravity.LEFT
        })
        mid.addView(TextView(this@MainActivity).apply {
            text = if (d.type == 0) t("زما باندې پور", "قرض من", "I owe") else t("پر ما باندې پور", "طلب از من", "Owed to me")
            textSize = 12f
            setTextColor(if (d.type == 0) red else green)
            gravity = if (lang == 0 || lang == 1) Gravity.RIGHT else Gravity.LEFT
            setPadding(0,dp(2),0,0)
        })
        row.addView(mid, LinearLayout.LayoutParams(0,dp(52),1f).apply { setMargins(dp(10),0,dp(8),0) })

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        right.addView(TextView(this@MainActivity).apply {
            text = money(d.remaining)
            textSize = 14.5f
            setTypeface(null,1)
            setTextColor(if (dark) Color.WHITE else Color.rgb(25,35,50))
            gravity = Gravity.CENTER
        })
        right.addView(TextView(this@MainActivity).apply {
            text = if (d.remaining <= 0) t("بشپړ", "تسویه", "Paid") else t("پاتې", "باقی", "Remaining")
            textSize = 10.5f
            setTextColor(if (d.remaining <= 0) green else red)
            gravity = Gravity.CENTER
            background = rounded(if (d.remaining <= 0) Color.rgb(225,247,238) else Color.rgb(255,235,237), 10)
            setPadding(dp(7),dp(3),dp(7),dp(3))
        })
        row.addView(right, LinearLayout.LayoutParams(dp(95),dp(54)))
        root.addView(row, LinearLayout.LayoutParams(-1,dp(70)).apply { setMargins(0,dp(4),0,dp(4)) })
    }

    private fun addDebt(old:Debt?) {
        page(if(old==null)t("نوی پور","قرض جدید","New Debt") else t("د پور سمون","ویرایش قرض","Edit Debt"))
        val name=EditText(this).apply{hint=t("د شخص نوم","نام شخص","Person name");setText(old?.person?:"")}
        val phone=EditText(this).apply{hint=t("موبایل شمېره","شماره موبایل","Phone");setText(old?.phone?:"");inputType=2}
        val amount=EditText(this).apply{hint=t("اصلي مبلغ","مبلغ اصلی","Principal amount");setText(if(old==null)"" else old.amount.toString());inputType=2 or InputType.TYPE_NUMBER_FLAG_DECIMAL}
        val type=Spinner(this)
        type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf(
            t("زما باندې پور دی","من قرضدار هستم","I Owe"),
            t("پر ما باندې پور دی","از من طلب دارد","Owed to Me")
        ))
        type.setSelection(old?.type?:0)
        val due=EditText(this).apply{hint=t("د ورکړې نېټه YYYY-MM-DD (اختیاري)","تاریخ سررسید YYYY-MM-DD","Due date YYYY-MM-DD");setText(if(old==null||old.dueDate==0L)"" else date(old.dueDate))}
        val note=EditText(this).apply{hint=t("یادښت","یادداشت","Note");setText(old?.note?:"")}
        root.addView(name);root.addView(phone);root.addView(amount);root.addView(type);root.addView(due);root.addView(note)
        button(t("✓ خوندي کول","✓ ذخیره","✓ Save"),{
            val n=name.text.toString().trim(); val a=amount.text.toString().toDoubleOrNull()
            if(n.isEmpty()||a==null||a<=0){toast(t("نوم او مبلغ سم ولیکه","نام و مبلغ را درست وارد کنید","Enter a valid name and amount"));return@button}
            val d=old?:Debt(System.currentTimeMillis(),n,"",a,0.0,type.selectedItemPosition,System.currentTimeMillis(),0,"")
            d.person=n;d.phone=phone.text.toString();d.amount=a;d.type=type.selectedItemPosition;d.note=note.text.toString()
            val ds=parseDate(due.text.toString()); if(due.text.toString().isBlank()) d.dueDate=0 else if(ds!=0L)d.dueDate=ds
            if(old==null) debts.add(d)
            save();home()
        },true)
        if(old!=null) button(t("🗑 حذف حساب","🗑 حذف حساب","🗑 Delete Account")) {
            AlertDialog.Builder(this).setTitle(t("حذف حساب؟","حذف حساب؟","Delete account?"))
                .setMessage(t("دا کار بېرته نه راګرځي.","این کار برگشت‌پذیر نیست.","This cannot be undone."))
                .setPositiveButton(t("هو","بلی","Yes")){_,_->debts.remove(old);payments.removeAll{it.debtId==old.id};save();home()}
                .setNegativeButton(t("نه","نخیر","No"),null).show()
        }
    }

    private fun parseDate(s:String):Long {
        return try { SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(s)?.time ?: 0 } catch(_:Exception){0}
    }

    private fun accounts(list:List<Debt>) {
        page(t("حسابونه","حساب‌ها","Accounts"))
        if(list.isEmpty()) {root.addView(txt(t("هیڅ حساب نشته.","حسابی وجود ندارد.","No accounts found."),17f));return}
        val sorted=list.sortedByDescending{it.date}
        sorted.forEach { d ->
            val status=if(d.remaining<=0.0)t("بشپړ شوی","تسویه شده","Paid") else t("پاتې","باقی","Remaining")
            card("${d.person}\n${if(d.phone.isBlank())"" else d.phone+"\n"}${t("پاتې","باقی","Remaining")}: ${money(d.remaining)}  •  $status"){detail(d)}
        }
    }

    private fun detail(d:Debt) {
        page(d.person)
        val role=if(d.type==0)t("زه پرې پوروړی یم","من قرضدار هستم","I Owe") else t("هغه پر ما پوروړی دی","او به من بدهکار است","Owed to Me")
        root.addView(txt("$role\n${t("اصلي مبلغ","مبلغ اصلی","Principal")}: ${money(d.amount)}\n${t("ورکړل شوي","پرداخت شده","Paid")}: ${money(d.paid)}\n${t("پاتې","باقی","Remaining")}: ${money(d.remaining)}\n${t("ثبت","ثبت","Created")}: ${date(d.date)}\n${t("سررسید","سررسید","Due")}: ${date(d.dueDate)}\n${t("یادښت","یادداشت","Note")}: ${d.note}",17f))
        button(t("＋ تادیه ثبت کړه","＋ ثبت پرداخت","＋ Add Payment"),{payment(d)},true)
        button(t("💳 د تادیاتو تاریخ","💳 تاریخ پرداخت‌ها","💳 Payment History")){paymentHistory(d)}
        button(t("✏️ سمون","✏️ ویرایش","✏️ Edit")){addDebt(d)}
        button(t("بېرته","برگشت","Back")){home()}
    }

    private fun choosePayment() {
        if(debts.isEmpty()){toast(t("لومړی پور اضافه کړه","اول قرض ثبت کنید","Add a debt first"));return}
        page(t("حساب وټاکئ","انتخاب حساب","Choose Account"))
        debts.forEach{d->card("${d.person} — ${money(d.remaining)}"){payment(d)}}
    }

    private fun payment(d:Debt) {
        if(d.remaining<=0){toast(t("دا پور بشپړ شوی.","این قرض تسویه شده.","This debt is already paid."));return}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(24),dp(24),dp(24))}
        val amount=EditText(this).apply{hint=t("مبلغ","مبلغ","Amount");inputType=2 or InputType.TYPE_NUMBER_FLAG_DECIMAL}
        val note=EditText(this).apply{hint=t("یادښت (اختیاري)","یادداشت","Note (optional)")}
        box.addView(amount);box.addView(note)
        AlertDialog.Builder(this).setTitle(t("تادیه ثبت کړه","ثبت پرداخت","Add Payment")).setView(box)
            .setPositiveButton(t("ثبت","ثبت","Save")){_,_->
                val p=amount.text.toString().toDoubleOrNull()
                if(p!=null&&p>0){
                    val actual=p.coerceAtMost(d.remaining);d.paid+=actual
                    payments.add(Payment(System.currentTimeMillis(),d.id,actual,System.currentTimeMillis(),note.text.toString()))
                    save();detail(d)
                }
            }.setNegativeButton(t("لغوه","لغو","Cancel"),null).show()
    }

    private fun paymentHistory(d:Debt) {
        page(t("د تادیاتو تاریخ","تاریخ پرداخت‌ها","Payment History"))
        val list=payments.filter{it.debtId==d.id}.sortedByDescending{it.date}
        if(list.isEmpty()) root.addView(txt(t("تر اوسه تادیه نشته.","پرداختی ثبت نشده.","No payments yet."),17f))
        list.forEach{p->card("${money(p.amount)}\n${date(p.date)}\n${p.note}")}
        button(t("بېرته","برگشت","Back")){detail(d)}
    }

    private fun reports() {
        page(t("راپورونه","گزارش‌ها","Reports"))
        val now=Calendar.getInstance()
        val today=debts.filter{sameDay(it.date,now)}
        val month=debts.filter{val c=Calendar.getInstance();c.timeInMillis=it.date;c.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&c.get(Calendar.MONTH)==now.get(Calendar.MONTH)}
        val year=debts.filter{val c=Calendar.getInstance();c.timeInMillis=it.date;c.get(Calendar.YEAR)==now.get(Calendar.YEAR)}
        root.addView(txt("${t("نن","امروز","Today")}: ${today.size} ${t("ثبتونه","ثبت","entries")}\n${t("میاشت","ماه","Month")}: ${month.size}\n${t("کال","سال","Year")}: ${year.size}\n\n${t("ټول اصلي پور","کل قرض اصلی","Total principal")}: ${money(debts.sumOf{it.amount})}\n${t("ټولې تادیې","کل پرداخت","Total payments")}: ${money(payments.sumOf{it.amount})}\n${t("ټول پاتې","کل باقی","Total remaining")}: ${money(debts.sumOf{it.remaining})}",18f))
        button(t("📋 ټول حسابونه","📋 همه حساب‌ها","📋 All Accounts")){accounts(debts)}
        button(t("بېرته","برگشت","Back")){home()}
    }

    private fun sameDay(ms:Long,c:Calendar):Boolean {
        val x=Calendar.getInstance();x.timeInMillis=ms
        return x.get(Calendar.YEAR)==c.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==c.get(Calendar.DAY_OF_YEAR)
    }

    private fun search() {
        page(t("لټون","جستجو","Search"))
        val e=EditText(this).apply{hint=t("نوم یا موبایل","نام یا موبایل","Name or phone")}
        root.addView(e)
        button(t("لټون","جستجو","Search"),{
            val q=e.text.toString().trim()
            accounts(debts.filter{it.person.contains(q,true)||it.phone.contains(q,true)})
        },true)
    }

    private fun settings() {
        page(t("تنظیمات","تنظیمات","Settings"))
        button(t("🌐 ژبه: ${if(lang==0)"پښتو" else if(lang==1)"دری" else "English"}","🌐 زبان","🌐 Language")){language()}
        button(if(dark)t("☀️ Light Mode","☀️ حالت روشن","☀️ Light Mode") else t("🌙 Dark Mode","🌙 حالت تاریک","🌙 Dark Mode")){dark=!dark;save();settings()}
        button(t("🔐 PIN قفل","🔐 قفل PIN","🔐 PIN Lock")){pinSettings()}
        button(t("💾 Backup جوړول","💾 ایجاد پشتیبان","💾 Create Backup")){backup()}
        button(t("♻️ Backup Restore","♻️ بازیابی پشتیبان","♻️ Restore Backup")){restore()}
        button(t("ℹ️ د اپ معلومات","ℹ️ درباره برنامه","ℹ️ About")){about()}
        button(t("بېرته","برگشت","Back")){home()}
    }

    private fun language() {
        page(t("ژبه","زبان","Language"))
        button("پښتو"){lang=0;save();home()}
        button("دری"){lang=1;save();home()}
        button("English"){lang=2;save();home()}
    }

    private fun pinSettings() {
        page(t("PIN قفل","قفل PIN","PIN Lock"))
        val e=EditText(this).apply{hint="4-6 digits";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD;setText(pin)}
        root.addView(e)
        button(t("خوندي کول","ذخیره","Save"),{
            val p=e.text.toString()
            if(p.isBlank()){pin="";prefs.edit().putBoolean("lock_enabled",false).apply()}
            else if(p.length in 4..6){pin=p;prefs.edit().putBoolean("lock_enabled",true).apply()}
            else {toast(t("PIN باید 4 تر 6 عدد وي","PIN باید 4 تا 6 رقم باشد","PIN must be 4-6 digits"));return@button}
            save();home()
        },true)
        button(t("PIN بندول","غیرفعال کردن PIN","Disable PIN")){pin="";prefs.edit().putBoolean("lock_enabled",false).apply();save();home()}
    }

    private fun pinScreen() {
        page(t("قفل","قفل","Locked"),false)
        root.addView(txt(t("د اپ د خلاصولو لپاره PIN ولیکه","برای باز کردن PIN را وارد کنید","Enter PIN to unlock"),18f))
        val e=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD;hint="PIN"}
        root.addView(e)
        button(t("خلاصول","باز کردن","Unlock"),{
            if(e.text.toString()==pin){unlocked=true;home()}else toast(t("PIN ناسم دی","PIN نادرست است","Wrong PIN"))
        },true)
    }

    private fun backup() {
        val text="QARZ_MANAGER_BACKUP_V1\nLANG=$lang\nDARK=$dark\nPIN=$pin\nDEBTS=${prefs.getString("debts","")}\nPAYMENTS=${prefs.getString("payments","")}"
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="text/plain";putExtra(Intent.EXTRA_TITLE,"qarz_manager_backup.txt")}
        pendingBackup=text;startActivityForResult(i,1001)
    }

    private var pendingBackup=""
    private fun restore() {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="text/plain";addCategory(Intent.CATEGORY_OPENABLE)}
        startActivityForResult(i,1002)
    }

    override fun onActivityResult(req:Int,res:Int,data:Intent?) {
        super.onActivityResult(req,res,data)
        if(res!=RESULT_OK||data?.data==null)return
        try {
            if(req==1001){
                contentResolver.openOutputStream(data.data!!)?.use{it.write(pendingBackup.toByteArray())}
                toast(t("Backup جوړ شو.","پشتیبان ساخته شد.","Backup created."))
            } else if(req==1002){
                val s=contentResolver.openInputStream(data.data!!)?.bufferedReader()?.readText()?:""
                if(!s.startsWith("QARZ_MANAGER_BACKUP_V1")){toast("Invalid backup");return}
                fun value(k:String)=s.lines().firstOrNull{it.startsWith("$k=")}?.substringAfter("=") ?: ""
                prefs.edit().putInt("lang",value("LANG").toIntOrNull()?:0).putBoolean("dark",value("DARK").toBoolean())
                    .putString("pin",value("PIN")).putString("debts",value("DEBTS")).putString("payments",value("PAYMENTS")).apply()
                load();home();toast(t("Backup Restore شو.","پشتیبان بازیابی شد.","Backup restored."))
            }
        }catch(e:Exception){toast(t("Backup ناکام شو.","بازیابی ناموفق بود.","Backup operation failed."))}
    }

    private fun about() {
        AlertDialog.Builder(this).setTitle("Qarz Manager")
            .setMessage(t("د پورونو د مدیریت اپ\nنسخه 4.0\nپښتو، دري او English\nOffline storage + Backup + PIN + Reports",
                "برنامه مدیریت قرض\nنسخه 4.0\nپښتو، دری و English\nذخیره آفلاین + پشتیبان + PIN + گزارش",
                "Debt Manager\nVersion 4.0\nPashto, Dari and English\nOffline storage + Backup + PIN + Reports"))
            .setPositiveButton("OK",null).show()
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
