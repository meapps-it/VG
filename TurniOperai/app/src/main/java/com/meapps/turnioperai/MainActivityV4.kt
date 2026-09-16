package com.meapps.turnioperai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val CBlue = Color(0xFF4069E5)
private val CGreen = Color(0xFF22A66A)
private val COrange = Color(0xFFF0A11D)
private val CPurple = Color(0xFF8056E8)
private val CRed = Color(0xFFDE5058)
private val CTeal = Color(0xFF0D9B95)
private val CNavy = Color(0xFF17325C)

class MainActivityV4 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiV4(this) }
    }
}

private fun typeVisual(type: ShiftType): Triple<Color, Color, ImageVector> = when(type) {
    ShiftType.MORNING -> Triple(Color(0xFFE6F7EE), CGreen, Icons.Default.WbSunny)
    ShiftType.AFTERNOON -> Triple(Color(0xFFFFF2D8), COrange, Icons.Default.LightMode)
    ShiftType.NIGHT -> Triple(Color(0xFFE8EEFF), CBlue, Icons.Default.DarkMode)
    ShiftType.DAY -> Triple(Color(0xFFE3F7F5), CTeal, Icons.Default.Work)
    ShiftType.REST -> Triple(Color(0xFFF0F2F5), Color(0xFF687386), Icons.Default.Hotel)
    ShiftType.VACATION -> Triple(Color(0xFFF1EAFF), CPurple, Icons.Default.BeachAccess)
    ShiftType.SICK -> Triple(Color(0xFFFFE8EA), CRed, Icons.Default.MedicalServices)
    ShiftType.PERMIT -> Triple(Color(0xFFFFF0E2), Color(0xFFD97706), Icons.Default.EventAvailable)
    ShiftType.ON_CALL -> Triple(Color(0xFFE6F5FF), Color(0xFF0284C7), Icons.Default.PhoneInTalk)
    ShiftType.SPLIT -> Triple(Color(0xFFFFEDF6), Color(0xFFD72D7A), Icons.Default.CallSplit)
    ShiftType.HOLIDAY -> Triple(Color(0xFFFFE8E8), Color(0xFFC92A2A), Icons.Default.Celebration)
    ShiftType.DOUBLE -> Triple(Color(0xFFEDEBFF), Color(0xFF6756D9), Icons.Default.DoubleArrow)
}

@Composable
fun TurniOperaiV4(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    fun save(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()
    }
    val light = lightColorScheme(primary=CBlue,secondary=CGreen,tertiary=CPurple,background=Color(0xFFF7F9FC),surface=Color.White)
    MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Color(0xFF9FB5FF)) else light) {
        var tab by remember { mutableIntStateOf(0) }
        var editDate by remember { mutableStateOf<LocalDate?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        if(editDate != null) {
            EditShiftV4(editDate!!, shifts.find { it.date == editDate }, { editDate=null },
                { e -> save(shifts.filterNot { it.date==e.date } + e); editDate=null },
                { save(shifts.filterNot { it.date==editDate }); editDate=null })
        } else {
            Scaffold(
                topBar = {
                    Surface(color = CNavy) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                            Surface(color=Color.White.copy(alpha=.16f), shape=RoundedCornerShape(14.dp), modifier=Modifier.size(44.dp)) {
                                Box(contentAlignment=Alignment.Center) { Icon(Icons.Default.CalendarMonth,null,tint=Color.White) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Turni Operai",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)
                                Text("Il tuo calendario di lavoro",color=Color.White.copy(alpha=.78f),style=MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                IconButton({menuOpen=true}) { Icon(Icons.Default.Menu,"Menu",tint=Color.White) }
                                DropdownMenu(menuOpen,{menuOpen=false}) {
                                    DropdownMenuItem({Text("Home")},{tab=0;menuOpen=false},leadingIcon={Icon(Icons.Default.Home,null)})
                                    DropdownMenuItem({Text("Statistiche")},{tab=1;menuOpen=false},leadingIcon={Icon(Icons.Default.BarChart,null)})
                                    DropdownMenuItem({Text("Calendario")},{tab=2;menuOpen=false},leadingIcon={Icon(Icons.Default.CalendarMonth,null)})
                                    DropdownMenuItem({Text("Impostazioni turni")},{tab=3;menuOpen=false},leadingIcon={Icon(Icons.Default.Settings,null)})
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        listOf(Triple("Home",Icons.Default.Home,0),Triple("Statistiche",Icons.Default.BarChart,1),Triple("Calendario",Icons.Default.CalendarMonth,2),Triple("Impostazioni",Icons.Default.Settings,3)).forEach { (l,i,n) ->
                            NavigationBarItem(tab==n,{tab=n},{Icon(i,l)},{Text(l)})
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when(tab) {
                        0 -> HomeV4(shifts){editDate=it}
                        1 -> StatsV4(shifts)
                        2 -> CalendarV4(shifts){editDate=it}
                        else -> SettingsV4(theme,{theme=it;prefs.edit().putString("theme",it).apply()},
                            onWholeWeek={start,seq,weeks,workDays ->
                                val mon=start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val total=weeks*7
                                val range=(0 until total).map{mon.plusDays(it.toLong())}.toSet(); val base=shifts.filterNot{it.date in range}
                                val generated=buildList { repeat(weeks){w -> val t=seq[w%seq.size]; repeat(7){d -> val date=mon.plusDays((w*7L)+d); add(ShiftEntry(date,if(date.dayOfWeek in workDays)t else ShiftType.REST)) } } }
                                save(base+generated)
                            },
                            onDayCycle={start,cycle,days ->
                                val range=(0 until days).map{start.plusDays(it.toLong())}.toSet(); val base=shifts.filterNot{it.date in range}
                                save(base+(0 until days).map{i->ShiftEntry(start.plusDays(i.toLong()),cycle[i%cycle.size])})
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeV4(shifts: List<ShiftEntry>, onDate:(LocalDate)->Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val name=month.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.uppercase()}+" ${month.year}"
    val ms=shifts.filter{YearMonth.from(it.date)==month}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { ElevatedCard(shape=RoundedCornerShape(22.dp)) { Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                IconButton({month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)}; Text(name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge); IconButton({month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)} }
            MonthGridV4(month,shifts,onDate)
        } } }
        item { SummaryV4(ms) }
        item { Button({onDate(LocalDate.now())},Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Aggiungi turno",fontWeight=FontWeight.Bold)} }
    }
}

@Composable
private fun MonthGridV4(month:YearMonth, shifts:List<ShiftEntry>, onDate:(LocalDate)->Unit) {
    val offset=month.atDay(1).dayOfWeek.value-1; val days=month.lengthOfMonth(); val labels=listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom")
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()){labels.forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelSmall)}}
        repeat((offset+days+6)/7){r -> Row(Modifier.fillMaxWidth()) { repeat(7){c -> val day=r*7+c-offset+1
            if(day in 1..days){ val date=month.atDay(day); val s=shifts.find{it.date==date}; val vis=s?.let{typeVisual(it.type)}
                Column(Modifier.weight(1f).padding(2.dp).height(56.dp).background(vis?.first?:Color(0xFFF5F6F8),RoundedCornerShape(12.dp)).clickable{onDate(date)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Text(day.toString(),fontWeight=FontWeight.Bold)
                    if(s!=null){ Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){ Text(s.type.short,color=vis!!.second,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold); if(s.overtime){Spacer(Modifier.width(2.dp));Icon(Icons.Default.Bolt,"Straordinario",tint=COrange,modifier=Modifier.size(13.dp))} } }
                }
            } else Spacer(Modifier.weight(1f).height(56.dp))
        } } }
    }
}

@Composable private fun SummaryV4(shifts:List<ShiftEntry>) {
    val workTypes=setOf(ShiftType.MORNING,ShiftType.AFTERNOON,ShiftType.NIGHT,ShiftType.DAY,ShiftType.SPLIT,ShiftType.HOLIDAY,ShiftType.DOUBLE)
    val work=shifts.count{it.type in workTypes}; val night=shifts.count{it.type==ShiftType.NIGHT}; val ot=shifts.count{it.overtime}; val rest=shifts.count{it.type==ShiftType.REST}
    ElevatedCard(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp)){Text("Riepilogo mese",fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        StatBadge("Ore","${work*8}",Icons.Default.Schedule,Color(0xFFE6F7EE),CGreen,Modifier.weight(1f));StatBadge("Notti","$night",Icons.Default.DarkMode,Color(0xFFE8EEFF),CBlue,Modifier.weight(1f));StatBadge("Straord.","$ot",Icons.Default.Bolt,Color(0xFFFFF2D8),COrange,Modifier.weight(1f));StatBadge("Riposi","$rest",Icons.Default.Hotel,Color(0xFFF1EAFF),CPurple,Modifier.weight(1f))
    }}}
}
@Composable private fun StatBadge(label:String,value:String,icon:ImageVector,bg:Color,fg:Color,modifier:Modifier){Column(modifier.background(bg,RoundedCornerShape(14.dp)).padding(9.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=fg,modifier=Modifier.size(18.dp));Text(value,fontWeight=FontWeight.ExtraBold,color=fg);Text(label,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center)}}

@Composable private fun StatsV4(shifts:List<ShiftEntry>){val month=shifts.filter{YearMonth.from(it.date)==YearMonth.now()};val counts=month.groupingBy{it.type}.eachCount();LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Statistiche",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)};item{SummaryV4(month)};items(ShiftType.entries){t->val(bg,fg,ic)=typeVisual(t);Card(colors=CardDefaults.cardColors(containerColor=bg),shape=RoundedCornerShape(15.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=fg);Spacer(Modifier.width(10.dp));Text(t.label,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text("${counts[t]?:0}",color=fg,fontWeight=FontWeight.ExtraBold)}}}}}

@Composable private fun CalendarV4(shifts:List<ShiftEntry>,onDate:(LocalDate)->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Calendario",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)};if(shifts.isEmpty())item{Text("Nessun turno inserito.")};items(shifts.sortedByDescending{it.date}){s->val(bg,fg,ic)=typeVisual(s.type);Card(colors=CardDefaults.cardColors(containerColor=bg),modifier=Modifier.clickable{onDate(s.date)},shape=RoundedCornerShape(15.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=fg);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(s.type.label,fontWeight=FontWeight.Bold);if(s.overtime){Spacer(Modifier.width(7.dp));Surface(color=Color(0xFFFFE2AA),shape=RoundedCornerShape(20.dp)){Row(Modifier.padding(horizontal=7.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Bolt,null,tint=Color(0xFFB76E00),modifier=Modifier.size(14.dp));Text("Straordinario",color=Color(0xFF8A5300),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}}};};Text(s.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.ITALIAN)).replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null,tint=fg)}}}}}

@Composable private fun EditShiftV4(date:LocalDate,existing:ShiftEntry?,onBack:()->Unit,onSave:(ShiftEntry)->Unit,onDelete:()->Unit){var type by remember{mutableStateOf(existing?.type?:ShiftType.MORNING)};var overtime by remember{mutableStateOf(existing?.overtime?:false)};Scaffold(topBar={TopAppBar(title={Text(if(existing==null)"Aggiungi turno" else "Modifica turno")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,null)}})}){pad->LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.ITALIAN)).replaceFirstChar{it.uppercase()},fontWeight=FontWeight.Bold)};item{Text("Scegli il tipo di giornata",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(8.dp));ShiftType.entries.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{st->val(bg,fg,ic)=typeVisual(st);Surface(color=if(type==st)bg else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),tonalElevation=if(type==st)2.dp else 0.dp,border=androidx.compose.foundation.BorderStroke(1.dp,if(type==st)fg else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.weight(1f).clickable{type=st}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=fg);Spacer(Modifier.width(8.dp));Text(st.label,fontWeight=FontWeight.SemiBold)}}};if(row.size==1)Spacer(Modifier.weight(1f))};Spacer(Modifier.height(8.dp))};item{Surface(color=if(overtime)Color(0xFFFFF2D8) else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{overtime=!overtime}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Bolt,null,tint=COrange);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Straordinario",fontWeight=FontWeight.Bold);Text("Comparirà nel calendario con il simbolo del fulmine",style=MaterialTheme.typography.bodySmall)};Switch(overtime,{overtime=it})}}};item{Button({onSave(ShiftEntry(date,type,overtime))},Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.Save,null);Spacer(Modifier.width(7.dp));Text("Salva turno",fontWeight=FontWeight.Bold)}};if(existing!=null)item{OutlinedButton(onDelete,Modifier.fillMaxWidth()){Icon(Icons.Default.Delete,null);Spacer(Modifier.width(6.dp));Text("Elimina turno")}}}}}

@Composable private fun SettingsV4(theme:String,onTheme:(String)->Unit,onWholeWeek:(LocalDate,List<ShiftType>,Int,Set<DayOfWeek>)->Unit,onDayCycle:(LocalDate,List<ShiftType>,Int)->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Impostazione turni",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Scegli prima come lavori. Poi imposta il ciclo.",color=MaterialTheme.colorScheme.onSurfaceVariant)};item{SettingsBlock("1. Rotazione per settimane","Una settimana tutta Mattino. La successiva tutta Notte. Poi Pomeriggio.",Icons.Default.ViewWeek,CGreen){WholeWeekPlannerV4(onWholeWeek)}};item{SettingsBlock("2. Rotazione per giorni","Per cicli tipo M P N R oppure 2M 2P 2N 2R.",Icons.Default.Autorenew,CPurple){DayCycleV4(onDayCycle)}};item{SettingsBlock("3. Legenda turni","Ogni categoria ha il suo colore e la sua icona.",Icons.Default.Category,COrange){ShiftType.entries.forEach{t->val(bg,fg,ic)=typeVisual(t);Surface(color=bg,shape=RoundedCornerShape(12.dp)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=fg);Spacer(Modifier.width(9.dp));Text(t.label,fontWeight=FontWeight.SemiBold,color=fg)}};Spacer(Modifier.height(5.dp))}};item{SettingsBlock("Aspetto","Tema dell'app",Icons.Default.Palette,CBlue){listOf("light" to "Chiaro","dark" to "Scuro","system" to "Automatico").forEach{(k,l)->Row(Modifier.fillMaxWidth().clickable{onTheme(k)}.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(theme==k,{onTheme(k)});Text(l)}}}};item{Text("Turni Operai 4.0",color=MaterialTheme.colorScheme.onSurfaceVariant)}}}

@Composable private fun SettingsBlock(title:String,subtitle:String,icon:ImageVector,accent:Color,content:@Composable ColumnScope.()->Unit){ElevatedCard(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(accent.copy(alpha=.12f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=accent)};Spacer(Modifier.width(12.dp));Column{Text(title,fontWeight=FontWeight.ExtraBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};Spacer(Modifier.height(2.dp));content()}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WholeWeekPlannerV4(onApply:(LocalDate,List<ShiftType>,Int,Set<DayOfWeek>)->Unit){val allowed=listOf(ShiftType.MORNING,ShiftType.AFTERNOON,ShiftType.NIGHT,ShiftType.DAY,ShiftType.REST);var sequence by remember{mutableStateOf(listOf(ShiftType.MORNING,ShiftType.NIGHT,ShiftType.AFTERNOON))};var start by remember{mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString())};var weeks by remember{mutableIntStateOf(12)};var workDays by remember{mutableStateOf(setOf(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY))};var msg by remember{mutableStateOf("")};Text("A. Ordine delle settimane",fontWeight=FontWeight.Bold);Text("Esempio selezionato: Mattino → Notte → Pomeriggio",style=MaterialTheme.typography.bodySmall);sequence.forEachIndexed{idx,current->var open by remember{mutableStateOf(false)};ExposedDropdownMenuBox(open,{open=!open}){val(bg,fg,ic)=typeVisual(current);OutlinedTextField(current.label,{},readOnly=true,label={Text("Settimana ${idx+1}")},leadingIcon={Icon(ic,null,tint=fg)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=bg.copy(alpha=.45f),unfocusedContainerColor=bg.copy(alpha=.35f)),modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(open,{open=false}){allowed.forEach{t->val(_,fg2,ic2)=typeVisual(t);DropdownMenuItem({Text(t.label)},{sequence=sequence.toMutableList().also{it[idx]=t};open=false},leadingIcon={Icon(ic2,null,tint=fg2)})}}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({if(sequence.size<6)sequence=sequence+ShiftType.REST},Modifier.weight(1f)){Icon(Icons.Default.Add,null);Text(" Aggiungi")};OutlinedButton({if(sequence.size>1)sequence=sequence.dropLast(1)},Modifier.weight(1f)){Icon(Icons.Default.Remove,null);Text(" Togli")}};HorizontalDivider();Text("B. Giorni lavorativi",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){DayOfWeek.entries.forEach{d->val sel=d in workDays;FilterChip(sel,{workDays=if(sel)workDays-d else workDays+d},{Text(d.getDisplayName(TextStyle.SHORT,Locale.ITALIAN).take(2).uppercase())})}};Text("C. Data di partenza",fontWeight=FontWeight.Bold);OutlinedTextField(start,{start=it},label={Text("Lunedì iniziale YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth());Text("D. Durata",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(4,8,12,26,52).forEach{n->FilterChip(weeks==n,{weeks=n},{Text(if(n==52)"1 anno" else "$n sett")})}};Surface(color=Color(0xFFE6F5FF),shape=RoundedCornerShape(14.dp)){Column(Modifier.fillMaxWidth().padding(12.dp)){Text("Anteprima",fontWeight=FontWeight.Bold,color=Color(0xFF0369A1));sequence.forEachIndexed{i,t->val(_,fg,ic)=typeVisual(t);Row(verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=fg,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("Settimana ${i+1}: ${t.label}")}}}};Button({val d=runCatching{LocalDate.parse(start)}.getOrNull();if(d==null)msg="Data non valida" else if(workDays.isEmpty())msg="Seleziona almeno un giorno lavorativo" else {onApply(d,sequence,weeks,workDays);msg="Rotazione settimanale applicata"}},Modifier.fillMaxWidth()){Icon(Icons.Default.Check,null);Spacer(Modifier.width(6.dp));Text("Applica rotazione",fontWeight=FontWeight.Bold)};if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.primary)}

@Composable private fun DayCycleV4(onApply:(LocalDate,List<ShiftType>,Int)->Unit){var raw by remember{mutableStateOf("M,P,N,R")};var start by remember{mutableStateOf(LocalDate.now().toString())};var days by remember{mutableIntStateOf(90)};var msg by remember{mutableStateOf("")};OutlinedTextField(raw,{raw=it.uppercase()},label={Text("Sequenza")},supportingText={Text("M mattina. P pomeriggio. N notte. R riposo.")},modifier=Modifier.fillMaxWidth());OutlinedTextField(start,{start=it},label={Text("Data inizio YYYY-MM-DD")},modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(30,90,180,365).forEach{n->FilterChip(days==n,{days=n},{Text(if(n==365)"1 anno" else "$n gg")})}};Button({val cycle=parseCycle(raw);val d=runCatching{LocalDate.parse(start)}.getOrNull();if(d==null)msg="Data non valida" else if(cycle.isEmpty())msg="Sequenza non valida" else{onApply(d,cycle,days);msg="Ciclo applicato"}},Modifier.fillMaxWidth()){Icon(Icons.Default.Autorenew,null);Spacer(Modifier.width(6.dp));Text("Genera ciclo")};if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.primary)}