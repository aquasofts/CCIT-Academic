package edu.ccit.webvpn

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.ccit.webvpn.core.academic.AcademicTimetable
import edu.ccit.webvpn.core.academic.TimetableCourse
import edu.ccit.webvpn.core.academic.TimetablePeriod
import edu.ccit.webvpn.core.ui.WebVpnColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimetableScreen(
    timetable: AcademicTimetable?,
    loading: Boolean,
    onLoad: (String?) -> Unit,
) {
    LaunchedEffect(timetable) {
        if (timetable == null && !loading) onLoad(null)
    }

    when {
        timetable == null && loading -> CenteredTimetableLoading()
        timetable == null -> EmptyTimetable(onLoad)
        else -> TimetableContent(timetable, loading, onLoad)
    }
}

@Composable
private fun TimetableContent(
    timetable: AcademicTimetable,
    loading: Boolean,
    onLoad: (String?) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val automaticSelection = remember(timetable.selectedTerm, today) {
        timetable.selectionForDate(today)
    }
    var selectedWeek by remember(timetable.selectedTerm) {
        mutableIntStateOf(automaticSelection.week)
    }
    var selectedDay by remember(timetable.selectedTerm) {
        mutableIntStateOf(automaticSelection.dayOfWeek)
    }
    var selectedCourse by remember { mutableStateOf<TimetableCourse?>(null) }
    val selectedDate = timetable.dateFor(selectedWeek, selectedDay)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        TimetableControls(
            timetable = timetable,
            selectedWeek = selectedWeek,
            selectedDay = selectedDay,
            selectedDate = selectedDate,
            today = today,
            loading = loading,
            onWeekChanged = { selectedWeek = it },
            onDayChanged = { selectedDay = it },
            onTermChanged = { onLoad(it) },
            onRefresh = { onLoad(timetable.selectedTerm) },
        )
        Spacer(Modifier.height(14.dp))
        SingleDayTimetable(
            timetable = timetable,
            selectedWeek = selectedWeek,
            selectedDay = selectedDay,
            selectedDate = selectedDate,
            onCourseClick = { selectedCourse = it },
        )
        if (timetable.note.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                color = WebVpnColors.Card,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, WebVpnColors.Stroke),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("备注", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(timetable.note, color = WebVpnColors.InkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    selectedCourse?.let { course ->
        CourseDetailDialog(course = course, onDismiss = { selectedCourse = null })
    }
}

@Composable
private fun TimetableControls(
    timetable: AcademicTimetable,
    selectedWeek: Int,
    selectedDay: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    loading: Boolean,
    onWeekChanged: (Int) -> Unit,
    onDayChanged: (Int) -> Unit,
    onTermChanged: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var termMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { termMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        timetable.selectedTerm.ifBlank { "当前学期" },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                }
                DropdownMenu(
                    expanded = termMenuExpanded,
                    onDismissRequest = { termMenuExpanded = false },
                ) {
                    timetable.terms.forEach { term ->
                        DropdownMenuItem(
                            text = { Text(term.label) },
                            onClick = {
                                termMenuExpanded = false
                                if (term.value != timetable.selectedTerm) onTermChanged(term.value)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新课表")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimetableDropdown(
                label = "周次",
                value = "第 $selectedWeek 周",
                items = (1..timetable.maxWeek).map { it to "第 $it 周" },
                onSelected = onWeekChanged,
                modifier = Modifier.weight(1f),
            )
            TimetableDropdown(
                label = "星期",
                value = "周${WeekdayShortNames[selectedDay - 1]}",
                items = (1..7).map { it to "周${WeekdayShortNames[it - 1]}" },
                onSelected = onDayChanged,
                modifier = Modifier.weight(1f),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WebVpnColors.Card,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, WebVpnColors.Stroke),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedDate?.format(DateLabelFormatter) ?: "日期未确定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "第 $selectedWeek 周 · 星期${WeekdayShortNames[selectedDay - 1]}",
                        style = MaterialTheme.typography.labelMedium,
                        color = WebVpnColors.InkMuted,
                    )
                }
                if (selectedDate == today) {
                    Surface(color = WebVpnColors.Rose, shape = MaterialTheme.shapes.small) {
                        Text(
                            "今天",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableDropdown(
    label: String,
    value: String,
    items: List<Pair<Int, String>>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = WebVpnColors.InkMuted)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.ExpandMore, contentDescription = "选择$label")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 150.dp),
        ) {
            items.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(key)
                    },
                )
            }
        }
    }
}

@Composable
private fun SingleDayTimetable(
    timetable: AcademicTimetable,
    selectedWeek: Int,
    selectedDay: Int,
    selectedDate: LocalDate?,
    onCourseClick: (TimetableCourse) -> Unit,
) {
    val dayCourses = timetable.courses.filter { course ->
        course.dayOfWeek == selectedDay &&
            (course.weekNumbers.isEmpty() || selectedWeek in course.weekNumbers)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(spring()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    "星期${WeekdayNames[selectedDay - 1]}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                selectedDate?.let {
                    Text(
                        it.format(DateLabelFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WebVpnColors.InkMuted,
                    )
                }
            }
            Text(
                if (dayCourses.isEmpty()) "当日无课" else "${dayCourses.size} 门课程",
                style = MaterialTheme.typography.labelLarge,
                color = WebVpnColors.InkMuted,
            )
        }

        timetable.periods.forEach { period ->
            SingleDayPeriodRow(
                period = period,
                courses = dayCourses.filter { it.periodIndex == period.index },
                onCourseClick = onCourseClick,
            )
        }
    }
}

@Composable
private fun SingleDayPeriodRow(
    period: TimetablePeriod,
    courses: List<TimetableCourse>,
    onCourseClick: (TimetableCourse) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WebVpnColors.Card,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, WebVpnColors.Stroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.width(66.dp).padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${period.startSection()}-${period.endSection()}节",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(5.dp))
                Text(period.startTime, style = MaterialTheme.typography.labelSmall, color = WebVpnColors.InkMuted)
                Text(period.endTime, style = MaterialTheme.typography.labelSmall, color = WebVpnColors.InkMuted)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (courses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("无课程", color = WebVpnColors.InkMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    courses.forEach { course ->
                        CourseBlock(course = course, onClick = { onCourseClick(course) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(course: TimetableCourse, onClick: () -> Unit) {
    val colors = listOf(
        WebVpnColors.Rose.copy(alpha = 0.72f),
        WebVpnColors.CardStrong,
        WebVpnColors.Success.copy(alpha = 0.22f),
        WebVpnColors.Brown.copy(alpha = 0.20f),
        Color(0xFFE6D8C8),
    )
    val color = colors[(course.name.hashCode() and Int.MAX_VALUE) % colors.size]
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = color,
        contentColor = WebVpnColors.Ink,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                course.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    "教室 · ${course.location}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (course.teacher.isNotBlank()) {
                Text(
                    "教师 · ${course.teacher}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WebVpnColors.InkMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(course: TimetableCourse, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DetailLine("时间", "第 ${course.startSection}-${course.endSection} 节")
                DetailLine("周次", course.weeks.ifBlank { "未标注" })
                DetailLine("教室", course.location.ifBlank { "未标注" })
                DetailLine("教师", course.teacher.ifBlank { "未标注" })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = WebVpnColors.InkMuted, modifier = Modifier.width(52.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CenteredTimetableLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在解析理论课表…", color = WebVpnColors.InkMuted)
        }
    }
}

@Composable
private fun EmptyTimetable(onLoad: (String?) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("课表暂时无法显示", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onLoad(null) }) { Text("重新加载") }
        }
    }
}

private fun TimetablePeriod.startSection(): Int = index * 2 - 1
private fun TimetablePeriod.endSection(): Int = index * 2

private val WeekdayShortNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val DateLabelFormatter = DateTimeFormatter.ofPattern("M月d日")
