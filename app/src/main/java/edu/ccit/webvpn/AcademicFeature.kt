package edu.ccit.webvpn

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.ui.graphics.vector.ImageVector

enum class AcademicFeature(
    val id: String,
    val title: String,
    val description: String,
    val path: String?,
) {
    Grades("grades", "成绩查询", "按学期查看课程成绩与绩点", null),
    Timetable("timetable", "学期理论课表", "查看本学期课程安排", "xskb/xskb_list.do"),
    CourseSelection("course_selection", "学生选课中心", "进入选课与选课轮次", "xsxk/xklc_list"),
    ClassroomRequest("classroom_request", "教室借用申请", "查询并申请可用教室", "kbxx/jsjy_query"),
    SelectionResults("selection_results", "选课结果查询", "查看课程预选与选中结果", "xkgl/xsxkjgcx"),
    Evaluation("evaluation", "学生评价", "完成课程教学评价", "xspj/xspj_find.do"),
    TextbookAccount("textbook_account", "教材账目信息", "查看教材费用与账目", "nxsjc/jczmxx"),
    TextbookConfirmation("textbook_confirmation", "学生教材确认", "核对并确认课程教材", "nxsjc/jccx"),
    MinorRegistration("minor_registration", "辅修报名", "查看辅修项目与报名信息", "fxgl/fxbmxx_query"),
    ;

    val icon: ImageVector
        get() = when (this) {
            Grades -> Icons.Default.Assessment
            Timetable -> Icons.AutoMirrored.Filled.EventNote
            CourseSelection -> Icons.Default.HowToReg
            ClassroomRequest -> Icons.Default.MeetingRoom
            SelectionResults -> Icons.AutoMirrored.Filled.FactCheck
            Evaluation -> Icons.Default.RateReview
            TextbookAccount -> Icons.Default.Book
            TextbookConfirmation -> Icons.Default.AutoStories
            MinorRegistration -> Icons.Default.Class
        }

    companion object {
        val defaults: List<AcademicFeature> = entries.toList()

        fun fromId(id: String): AcademicFeature? = entries.firstOrNull { it.id == id }
    }
}

class AcademicFeaturePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "academic_features",
        Context.MODE_PRIVATE,
    )

    fun loadFavorites(): Set<String> = preferences.getStringSet(FavoritesKey, emptySet())
        .orEmpty()
        .filterTo(linkedSetOf()) { AcademicFeature.fromId(it) != null }

    fun saveFavorites(ids: Set<String>) {
        preferences.edit().putStringSet(FavoritesKey, ids).apply()
    }

    fun loadOrder(): List<AcademicFeature> {
        val saved = preferences.getString(OrderKey, null)
            ?.split(',')
            .orEmpty()
            .mapNotNull(AcademicFeature::fromId)
            .distinct()
        return saved + AcademicFeature.defaults.filterNot(saved::contains)
    }

    fun saveOrder(features: List<AcademicFeature>) {
        preferences.edit().putString(OrderKey, features.joinToString(",") { it.id }).apply()
    }

    private companion object {
        const val FavoritesKey = "favorite_ids"
        const val OrderKey = "feature_order"
    }
}
