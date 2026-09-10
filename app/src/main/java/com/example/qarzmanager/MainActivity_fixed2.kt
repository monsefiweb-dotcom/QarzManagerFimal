
package com.example.qarzmanager

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
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

    private fun page(title:String, back:Boolean=true) {
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(16,16,16,12)
            setBackgroundColor(if(dark) bgDark else bgLight)
        }
        val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        if(back) {
            val b=ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_media_previous)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener{home()}
                contentDescription="Back"
            }
            bar.addView(b, LinearLayout.LayoutParams(48,48))
        }
        val h=TextView(this).apply {
            text=title; textSize=24f; setTextColor(if(dark) Color.WHITE else Color.rgb(20,25,36))
            setTypeface(null,1); gravity=Gravity.CENTER_VERTICAL
        }
        bar.addView(h, LinearLayout.LayoutParams(0,56,1f))
        root.addView(bar)
        setContentView(root)
    }

    private fun txt(s:String, size:Float=15f): TextView = TextView(this).apply {
        text=s; textSize=size
        setTextColor(if(dark) Color.WHITE else Color.rgb(30,35,45))
    }

    // The action lambda is the last parameter so Kotlin trailing-lambda calls work.
    private fun button(text:String, primary:Boolean=false, action:()->Unit) {
        val b=Button(this).apply {
            this.text=text; textSize=15f; isAllCaps=false
            setOnClickListener{action()}
            if(primary) setTextColor(Color.WHITE)
        }
        if(primary) b.setBackgroundColor(blue)
        root.addView(b, LinearLayout.LayoutParams(-1,54).apply{setMargins(0,5,0,5)})
    }

    // Compatibility overload for calls written as button(text, { ... }, true).
    private fun button(text:String, action:()->Unit, primary:Boolean) {
        button(text, primary, action)
    }

    private fun card(text:String, action:(()->Unit)?=null) {
        val tv=TextView(this).apply {
            this.text=text; textSize=15f; setPadding(18,16,18,16)
            setTextColor(if(dark) Color.WHITE else Color.rgb(25,30,40))
            setBackgroundColor(if(dark) Color.rgb(31,35,43) else Color.WHITE)
            if(action!=null) setOnClickListener{action()}
        }
        root.addView(tv, LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,6,0,6)})
    }

    private fun home() {
        page(t("د پورونو مدیریت","مدیریت قرض‌ها","Debt Manager"), false)

        val mine=debts.filter{it.type==0}.sumOf{it.remaining}
        val owed=debts.filter{it.type==1}.sumOf{it.remaining}
        val total=mine+owed

        val head=txt(t("خپل پورونه په اسانه مدیریت کړئ","قرض‌های خود را آسان مدیریت کنید","Manage your debts easily"),14f)
        root.addView(head)

        val summary=TextView(this).apply {
            text="${t("ټول پاتې پور","مجموع باقی","Total Remaining")}\n${money(total)}"
            textSize=28f; setTypeface(null,1); setTextColor(Color.WHITE); setPadding(20,18,20,18)
            setBackgroundColor(blue)
        }
        root.addView(summary,LinearLayout.LayoutParams(-1,110).apply{setMargins(0,14,0,8)})

        card("${t("زما باندې پورونه","قرض‌های من","I Owe")}: ${money(mine)}")
        card("${t("پر ما باندې پورونه","قرض به من","Owed to Me")}: ${money(owed)}")

        button(t("＋ پور اضافه کړه","＋ ثبت قرض","＋ Add Debt"),{addDebt(null)},true)
        button(t("💳 تادیه ثبت کړه","💳 ثبت پرداخت","💳 Add Payment")){choosePayment()}
        button(t("👥 حسابونه (${debts.size})","👥 حساب‌ها (${debts.size})","👥 Accounts (${debts.size})")){accounts(debts)}
        button(t("📊 راپورونه","📊 گزارش‌ها","📊 Reports")){reports()}
        button(t("🔎 لټون","🔎 جستجو","🔎 Search")){search()}
        button(t("⚙️ تنظیمات","⚙️ تنظیمات","⚙️ Settings")){settings()}

        val today=Calendar.getInstance()
        val due=debts.filter{it.remaining>0 && it.dueDate>0 && it.dueDate<=today.timeInMillis}
        if(due.isNotEmpty()) {
            card("⚠️ ${t("${due.size} حسابونه د ورکړې نېټه لري","${due.size} حساب سررسید شده","${due.size} accounts are due")}") { accounts(due) }
        }
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
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
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
