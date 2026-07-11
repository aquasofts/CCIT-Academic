package edu.ccit.webvpn

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.ccit.webvpn.core.academic.CourseGrade
import edu.ccit.webvpn.core.ui.WebVpnCard
import edu.ccit.webvpn.core.ui.WebVpnColors
import edu.ccit.webvpn.core.ui.WebVpnPrimaryButton
import edu.ccit.webvpn.core.ui.webVpnTextFieldColors

@Composable
fun AcademicSection(
    state: AcademicUiState,
    onRefreshCaptcha: () -> Unit,
    onLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onForgetSavedAccount: (String) -> Unit,
    onUseManualCredentials: () -> Unit,
    onSelectTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
) {
    WebVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = WebVpnColors.Brown)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("教务系统登录", style = MaterialTheme.typography.titleLarge)
                    Text("教学一体化服务平台", color = WebVpnColors.InkMuted)
                }
            }

            when {
                state.initializing -> AcademicLoading("正在恢复教务系统登录…")
                !state.loggedIn -> AcademicLoginForm(
                    state,
                    onRefreshCaptcha,
                    onLogin,
                    onSelectSavedAccount,
                    onForgetSavedAccount,
                    onUseManualCredentials,
                )
                else -> AcademicGradeFilters(
                    state = state,
                    onSelectTerm = onSelectTerm,
                    onBestOnlyChanged = onBestOnlyChanged,
                    onQueryGrades = onQueryGrades,
                )
            }
        }
    }

    if (state.loggedIn) {
        when {
            state.loadingGrades -> WebVpnCard(modifier = Modifier.fillMaxWidth()) {
                AcademicLoading("正在查询成绩…")
            }
            state.grades.isEmpty() -> WebVpnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "当前筛选条件下没有成绩记录。",
                    modifier = Modifier.padding(18.dp),
                    color = WebVpnColors.InkMuted,
                )
            }
            else -> state.grades.forEach { grade -> GradeCard(grade) }
        }
    }
}

@Composable
fun AcademicGradesSection(
    state: AcademicUiState,
    onSelectTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
) {
    WebVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = WebVpnColors.Brown)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("成绩查询", style = MaterialTheme.typography.titleLarge)
                    Text("按学期筛选课程成绩", color = WebVpnColors.InkMuted)
                }
            }
            AcademicGradeFilters(state, onSelectTerm, onBestOnlyChanged, onQueryGrades)
        }
    }

    when {
        state.loadingGrades -> WebVpnCard(modifier = Modifier.fillMaxWidth()) {
            AcademicLoading("正在查询成绩…")
        }
        state.grades.isEmpty() -> WebVpnCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "当前筛选条件下没有成绩记录。",
                modifier = Modifier.padding(18.dp),
                color = WebVpnColors.InkMuted,
            )
        }
        else -> state.grades.forEach { grade -> GradeCard(grade) }
    }
}

@Composable
private fun AcademicLoginForm(
    state: AcademicUiState,
    onRefreshCaptcha: () -> Unit,
    onLogin: (String, String, String, Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onForgetSavedAccount: (String) -> Unit,
    onUseManualCredentials: () -> Unit,
) {
    var username by remember { mutableStateOf(state.defaultUsername) }
    var password by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(false) }
    var captchaCode by remember(state.captcha) { mutableStateOf("") }
    val usingSavedPassword = state.selectedSavedUsername != null

    LaunchedEffect(state.defaultUsername, state.selectedSavedUsername) {
        if (state.defaultUsername.isNotBlank()) username = state.defaultUsername
        if (usingSavedPassword) {
            password = ""
            rememberPassword = false
        }
    }

    Text(
        "WebVPN 已连接，请继续登录学生端。学生端有独立验证码。",
        color = WebVpnColors.InkMuted,
    )
    if (state.savedAccounts.isNotEmpty()) {
        Text("已保存教务账号", style = MaterialTheme.typography.titleMedium)
        state.savedAccounts.forEach { account ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onSelectSavedAccount(account.username) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (state.selectedSavedUsername == account.username) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Person
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(account.username)
                }
                IconButton(onClick = { onForgetSavedAccount(account.username) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除已保存教务账号")
                }
            }
        }
    }
    OutlinedTextField(
        value = username,
        onValueChange = { username = it.trim() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("学号") },
        singleLine = true,
        enabled = !usingSavedPassword,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = webVpnTextFieldColors(),
    )
    if (usingSavedPassword) {
        WebVpnCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WebVpnColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Text("已使用本机加密保存的教务密码")
                }
                Text("只需填写本次验证码即可登录。", color = WebVpnColors.InkMuted)
                OutlinedButton(onClick = onUseManualCredentials, modifier = Modifier.fillMaxWidth()) {
                    Text("重新输入教务账号密码")
                }
            }
        }
    } else {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("教务系统密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            colors = webVpnTextFieldColors(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rememberPassword = !rememberPassword },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = rememberPassword,
                onCheckedChange = { rememberPassword = it },
            )
            Text("在本机加密保存此教务账号和密码")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = captchaCode,
            onValueChange = { captchaCode = it.trim() },
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onLogin(username, password, captchaCode, rememberPassword) },
            ),
            colors = webVpnTextFieldColors(),
        )
        Spacer(Modifier.width(10.dp))
        AcademicCaptcha(state.captcha, state.loadingCaptcha)
        IconButton(onClick = onRefreshCaptcha, enabled = !state.loadingCaptcha) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新教务验证码")
        }
    }
    WebVpnPrimaryButton(
        onClick = { onLogin(username, password, captchaCode, rememberPassword) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.submitting &&
            username.isNotBlank() &&
            (usingSavedPassword || password.isNotBlank()) &&
            state.captcha != null &&
            captchaCode.isNotBlank(),
    ) {
        if (state.submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = WebVpnColors.Surface,
            )
        } else {
            Icon(Icons.Default.School, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("登录学生端")
        }
    }
}

@Composable
private fun AcademicGradeFilters(
    state: AcademicUiState,
    onSelectTerm: (String) -> Unit,
    onBestOnlyChanged: (Boolean) -> Unit,
    onQueryGrades: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val termLabel = state.terms.firstOrNull { it.value == state.selectedTerm }?.label
        ?: "全部学期"

    Text("已登录学生端", color = WebVpnColors.Success, fontWeight = FontWeight.Bold)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(termLabel, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            state.terms.forEach { term ->
                DropdownMenuItem(
                    text = { Text(term.label) },
                    onClick = {
                        menuExpanded = false
                        onSelectTerm(term.value)
                    },
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBestOnlyChanged(!state.bestOnly) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.bestOnly, onCheckedChange = onBestOnlyChanged)
        Text("同一课程只显示最好成绩")
    }
    WebVpnPrimaryButton(
        onClick = onQueryGrades,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.loadingGrades,
    ) {
        Icon(Icons.Default.Search, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("查询成绩")
    }
}

@Composable
private fun AcademicCaptcha(bytes: ByteArray?, loading: Boolean) {
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
    }
    WebVpnCard(modifier = Modifier.size(width = 112.dp, height = 50.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                bitmap == null -> Text("点击刷新", color = WebVpnColors.InkMuted)
                else -> Image(bitmap = bitmap, contentDescription = "教务系统图形验证码")
            }
        }
    }
}

@Composable
private fun GradeCard(grade: CourseGrade) {
    WebVpnCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(grade.courseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(grade.semester, grade.courseCode)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        color = WebVpnColors.InkMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    grade.score.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = WebVpnColors.Brown,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                listOf(
                    "学分 ${grade.credit.ifBlank { "—" }}",
                    "绩点 ${grade.gradePoint.ifBlank { "—" }}",
                    grade.assessmentMethod,
                    grade.courseAttribute,
                ).filter(String::isNotBlank).joinToString("  ·  "),
                color = WebVpnColors.InkMuted,
            )
            listOf(grade.scoreMark, grade.description, grade.note)
                .filter(String::isNotBlank)
                .joinToString("；")
                .takeIf(String::isNotBlank)
                ?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AcademicLoading(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(message, color = WebVpnColors.InkMuted)
    }
}
