package com.appiconextractor

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appiconextractor.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用图标提取器 - 主Activity
 * 功能：一键提取手机内所有应用的图标并保存到本地
 * 支持：Android 7.0 ~ Android 16
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appListAdapter: AppListAdapter
    private val allAppList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private val selectedApps = mutableSetOf<String>()
    
    // 图标保存目录
    private lateinit var saveDir: File
    
    // 当前筛选模式
    private var currentFilter = FilterMode.ALL
    
    // 是否处于选择模式
    private var isSelectionMode = false

    // 搜索关键词
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initSaveDir()
        initRecyclerView()
        setupSearch()
        setupFilterChips()
        setupClickListeners()
        loadAppList()
    }

    /**
     * 初始化保存目录
     */
    private fun initSaveDir() {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        saveDir = File(picturesDir, "AppIcons")
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
    }

    /**
     * 初始化RecyclerView
     */
    private fun initRecyclerView() {
        appListAdapter = AppListAdapter(
            appList = filteredAppList,
            isSelectionMode = { isSelectionMode },
            selectedApps = selectedApps,
            onItemClick = { appInfo ->
                if (isSelectionMode) {
                    toggleSelection(appInfo)
                } else {
                    extractSingleIcon(appInfo)
                }
            },
            onItemLongClick = { appInfo ->
                if (!isSelectionMode) {
                    enterSelectionMode(appInfo)
                }
                true
            },
            onDownloadClick = { appInfo ->
                extractSingleIcon(appInfo)
            }
        )
        binding.rvAppList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appListAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * 设置搜索功能
     */
    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            applyFilter()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            hideKeyboard()
        }
    }

    /**
     * 执行搜索
     */
    private fun performSearch() {
        applyFilter()
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    /**
     * 设置筛选按钮
     */
    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipAll) -> FilterMode.ALL
                checkedIds.contains(R.id.chipUser) -> FilterMode.USER
                checkedIds.contains(R.id.chipSystem) -> FilterMode.SYSTEM
                checkedIds.contains(R.id.chipSelected) -> FilterMode.SELECTED
                else -> FilterMode.ALL
            }
            applyFilter()
        }
    }

    /**
     * 应用筛选
     */
    private fun applyFilter() {
        filteredAppList.clear()
        
        val filtered = when (currentFilter) {
            FilterMode.ALL -> allAppList.toList()
            FilterMode.USER -> allAppList.filter { !it.isSystemApp }
            FilterMode.SYSTEM -> allAppList.filter { it.isSystemApp }
            FilterMode.SELECTED -> allAppList.filter { selectedApps.contains(it.packageName) }
        }
        
        // 应用搜索
        val searchFiltered = if (searchQuery.isNotEmpty()) {
            filtered.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filtered
        }
        
        filteredAppList.addAll(searchFiltered)
        appListAdapter.notifyDataSetChanged()
        
        updateAppCountText()
    }

    /**
     * 更新应用数量文本
     */
    private fun updateAppCountText() {
        val text = if (searchQuery.isNotEmpty() || currentFilter != FilterMode.ALL) {
            "显示 ${filteredAppList.size} / 共 ${allAppList.size} 个应用"
        } else {
            "已加载 ${allAppList.size} 个应用"
        }
        binding.tvAppCount.text = text
    }

    /**
     * 设置按钮点击监听
     */
    private fun setupClickListeners() {
        // 提取全部图标
        binding.btnExtractAll.setOnClickListener {
            if (filteredAppList.isEmpty()) {
                Toast.makeText(this, R.string.no_apps, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExtractDialog(filteredAppList)
        }

        // 提取选中图标
        binding.btnExtractSelected.setOnClickListener {
            val selectedList = allAppList.filter { selectedApps.contains(it.packageName) }
            if (selectedList.isEmpty()) {
                Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExtractDialog(selectedList)
        }

        // 打开保存目录
        binding.btnOpenFolder.setOnClickListener {
            openSaveFolder()
        }

        // 全选按钮
        binding.btnSelectAll.setOnClickListener {
            if (selectedApps.size == filteredAppList.size) {
                // 已全选，取消全选
                selectedApps.clear()
            } else {
                // 全选当前列表
                filteredAppList.forEach { selectedApps.add(it.packageName) }
            }
            appListAdapter.notifyDataSetChanged()
            updateSelectionToolbar()
        }

        // 取消选择
        binding.btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }
    }

    /**
     * 进入选择模式
     */
    private fun enterSelectionMode(appInfo: AppInfo) {
        isSelectionMode = true
        selectedApps.add(appInfo.packageName)
        
        binding.selectionToolbar.visibility = View.VISIBLE
        binding.btnExtractAll.visibility = View.GONE
        binding.btnExtractSelected.visibility = View.VISIBLE
        
        appListAdapter.notifyDataSetChanged()
        updateSelectionToolbar()
    }

    /**
     * 退出选择模式
     */
    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedApps.clear()
        
        binding.selectionToolbar.visibility = View.GONE
        binding.btnExtractAll.visibility = View.VISIBLE
        binding.btnExtractSelected.visibility = View.GONE
        
        appListAdapter.notifyDataSetChanged()
    }

    /**
     * 切换选中状态
     */
    private fun toggleSelection(appInfo: AppInfo) {
        if (selectedApps.contains(appInfo.packageName)) {
            selectedApps.remove(appInfo.packageName)
            if (selectedApps.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            selectedApps.add(appInfo.packageName)
        }
        // 只更新当前项，避免全列表刷新导致乱跳
        val position = filteredAppList.indexOf(appInfo)
        if (position >= 0) {
            appListAdapter.notifyItemChanged(position)
        }
        updateSelectionToolbar()
    }

    /**
     * 更新选择工具栏
     */
    private fun updateSelectionToolbar() {
        binding.tvSelectedCount.text = getString(R.string.selected_count, selectedApps.size)
        
        // 更新全选按钮文本
        if (selectedApps.size == filteredAppList.size && filteredAppList.isNotEmpty()) {
            binding.btnSelectAll.text = "取消全选"
        } else {
            binding.btnSelectAll.text = "全选"
        }
    }

    /**
     * 加载已安装应用列表
     */
    private fun loadAppList() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.loading_apps)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                val tempList = mutableListOf<AppInfo>()
                
                for (appInfo in packages) {
                    try {
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val packageName = appInfo.packageName
                        val icon = pm.getApplicationIcon(appInfo)
                        
                        tempList.add(AppInfo(
                            appName = appName,
                            packageName = packageName,
                            icon = icon,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        ))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 按应用名排序
                tempList.sortBy { it.appName.lowercase(Locale.getDefault()) }

                withContext(Dispatchers.Main) {
                    allAppList.clear()
                    allAppList.addAll(tempList)
                    applyFilter()
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "加载失败: ${e.message}"
                    Toast.makeText(this@MainActivity, "加载应用列表失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 显示提取确认对话框
     */
    private fun showExtractDialog(appsToExtract: List<AppInfo>) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val folderName = "AppIcons_${dateFormat.format(Date())}"
        
        AlertDialog.Builder(this)
            .setTitle("提取图标")
            .setMessage("将提取 ${appsToExtract.size} 个应用的图标\n保存到: Pictures/$folderName/")
            .setPositiveButton("开始提取") { _, _ ->
                createNewSaveFolder(folderName)
                extractIcons(appsToExtract)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 创建新的保存文件夹
     */
    private fun createNewSaveFolder(folderName: String) {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        saveDir = File(picturesDir, folderName)
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }
    }

    /**
     * 提取应用图标
     */
    private fun extractIcons(appsToExtract: List<AppInfo>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.max = appsToExtract.size
        binding.progressBar.progress = 0
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.extracting)
        binding.btnExtractAll.isEnabled = false
        binding.btnExtractSelected.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            var failCount = 0

            for ((index, appInfo) in appsToExtract.withIndex()) {
                try {
                    val success = saveIcon(appInfo)
                    if (success) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                    e.printStackTrace()
                }

                // 更新进度
                withContext(Dispatchers.Main) {
                    binding.progressBar.progress = index + 1
                    binding.tvStatus.text = "正在提取: ${appInfo.appName} (${index + 1}/${appsToExtract.size})"
                }
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.btnExtractAll.isEnabled = true
                binding.btnExtractSelected.isEnabled = true
                
                val message = if (failCount == 0) {
                    getString(R.string.extract_success, successCount)
                } else {
                    "成功: $successCount, 失败: $failCount"
                }
                
                binding.tvStatus.text = message
                Toast.makeText(
                    this@MainActivity,
                    "$message\n保存路径: ${saveDir.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
                
                // 如果在选择模式下，提取后退出选择模式
                if (isSelectionMode) {
                    exitSelectionMode()
                }
            }
        }
    }

    /**
     * 提取单个应用图标
     */
    private fun extractSingleIcon(appInfo: AppInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            // 创建临时目录保存单个图标
            if (!saveDir.exists() || saveDir.name.startsWith("AppIcons").not()) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                saveDir = File(picturesDir, "AppIcons")
                if (!saveDir.exists()) {
                    saveDir.mkdirs()
                }
            }
            
            val success = saveIcon(appInfo)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(
                        this@MainActivity,
                        "已保存: ${appInfo.appName}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "保存失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 保存图标到文件
     */
    private fun saveIcon(appInfo: AppInfo): Boolean {
        return try {
            val icon = appInfo.icon
            val bitmap = drawableToBitmap(icon)
            
            if (bitmap != null) {
                val safeFileName = appInfo.packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val fileName = "${safeFileName}.png"
                val file = File(saveDir, fileName)
                
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Drawable转Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                is AdaptiveIconDrawable -> {
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 216
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 216
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
                else -> {
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 打开保存文件夹
     */
    private fun openSaveFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    saveDir
                )
            } else {
                Uri.fromFile(saveDir)
            }
            
            intent.setDataAndType(uri, "resource/folder")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val chooser = Intent.createChooser(intent, "选择文件管理器")
            startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "保存路径: ${saveDir.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 筛选模式枚举
     */
    enum class FilterMode {
        ALL, USER, SYSTEM, SELECTED
    }
}

/**
 * 应用信息数据类
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean
)

/**
 * 应用列表适配器 - 修复复用问题
 */
class AppListAdapter(
    private val appList: List<AppInfo>,
    private val isSelectionMode: () -> Boolean,
    private val selectedApps: Set<String>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: (AppInfo) -> Boolean,
    private val onDownloadClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val tvAppType: TextView = view.findViewById(R.id.tvAppType)
        val btnDownload: ImageButton = view.findViewById(R.id.btnDownload)
        
        // 存储当前绑定的应用包名，用于判断checkbox点击
        var currentPackageName: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]
        val context = holder.itemView.context
        
        // 保存当前包名
        holder.currentPackageName = appInfo.packageName
        
        holder.ivAppIcon.setImageDrawable(appInfo.icon)
        holder.tvAppName.text = appInfo.appName
        holder.tvPackageName.text = appInfo.packageName
        
        // 设置应用类型标签
        if (appInfo.isSystemApp) {
            holder.tvAppType.text = "系统应用"
            holder.tvAppType.setBackgroundResource(R.drawable.tag_background_system)
        } else {
            holder.tvAppType.text = "用户应用"
            holder.tvAppType.setBackgroundResource(R.drawable.tag_background)
        }
        
        // 选择模式
        val inSelectionMode = isSelectionMode()
        holder.checkbox.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        holder.btnDownload.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        
        // 先移除监听器，再设置状态，避免触发回调
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selectedApps.contains(appInfo.packageName)
        
        // 设置checkbox点击监听
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            // 确保是当前项的操作
            if (holder.currentPackageName == appInfo.packageName) {
                // 通过点击item来处理，保证逻辑统一
                onItemClick(appInfo)
            }
        }
        
        // 点击事件 - 使用点击位置判断
        holder.itemView.setOnClickListener {
            onItemClick(appInfo)
        }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick(appInfo)
        }
        
        holder.btnDownload.setOnClickListener {
            onDownloadClick(appInfo)
        }
    }

    override fun getItemCount() = appList.size
}
