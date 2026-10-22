package com.balanca.assistente.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderArchive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.balanca.assistente.data.Product
import com.balanca.assistente.data.RecentProduct
import com.balanca.assistente.ui.theme.*
import com.balanca.assistente.util.ImageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleAssistantApp(
    viewModel: ProductViewModel,
    onRequestMicrophonePermission: () -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val products by viewModel.filteredProducts.collectAsState()
    val recentProducts by viewModel.recentProducts.collectAsState()
    val selectedSpotlight by viewModel.selectedSpotlightProduct.collectAsState()
    val isAddEditDialogOpen by viewModel.isAddEditDialogOpen.collectAsState()
    val productToEdit by viewModel.productToEdit.collectAsState()
    val productToDelete by viewModel.productToDelete.collectAsState()
    val formErrorMessage by viewModel.formErrorMessage.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()
    val isListening by viewModel.isListening.collectAsState()

    var isDataBackupDialogOpen by remember { mutableStateOf(false) }

    // Launcher para salvar arquivo CSV exportado
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val csvData = viewModel.getCsvContent()
                    stream.write(csvData.toByteArray(Charsets.UTF_8))
                    Toast.makeText(context, "Arquivo CSV exportado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher para importar arquivo CSV com suporte amplo a gerenciadores de arquivos
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.importFromCsv(stream)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao abrir CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher para salvar backup completo ZIP (com fotos)
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    viewModel.exportZip(context, stream)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao exportar ZIP: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher para restaurar backup completo ZIP (com fotos)
    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.importZip(context, stream)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao abrir ZIP: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Copiar código para a área de transferência
    fun copyCode(code: String, productName: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Código de Balança", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Código $code copiado ($productName)", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Assistente de Balança",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryYellow
                            )
                        )
                        Text(
                            text = "${products.size} produto(s) cadastrado(s) • 100% Offline",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                },
                actions = {
                    // Botão Importar CSV na barra superior
                    IconButton(
                        onClick = { importCsvLauncher.launch("text/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Importar CSV",
                            tint = CodeHighlight
                        )
                    }
                    // Botão Exportar CSV na barra superior
                    IconButton(
                        onClick = { exportCsvLauncher.launch("produtos_balanca.csv") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Exportar CSV",
                            tint = AccentGreen
                        )
                    }
                    // Botão Backup Completo com Fotos
                    IconButton(
                        onClick = { isDataBackupDialogOpen = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderArchive,
                            contentDescription = "Backup e Fotos",
                            tint = TextLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = PrimaryYellow,
                contentColor = BackgroundDark,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar Produto",
                        modifier = Modifier.size(22.dp)
                    )
                },
                text = {
                    Text(
                        text = "Novo Produto",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                }
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Barra de Ações Rápidas: Importar CSV, Exportar CSV e Backup Fotos
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { importCsvLauncher.launch("text/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CodeHighlight),
                    border = BorderStroke(1.dp, SurfaceVariantDark),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CodeHighlight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Importar CSV",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = { exportCsvLauncher.launch("produtos_balanca.csv") },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    border = BorderStroke(1.dp, SurfaceVariantDark),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AccentGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Exportar CSV",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = { isDataBackupDialogOpen = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                    border = BorderStroke(1.dp, SurfaceVariantDark),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderArchive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryYellow
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Backup Fotos",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Barra de Busca Grande
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                placeholder = {
                    Text(
                        text = "Pesquisar produto ou código...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = TextMuted)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = PrimaryYellow,
                        modifier = Modifier.size(28.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearchQuery() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar busca",
                                    tint = TextMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (isListening) {
                                    viewModel.stopVoiceRecognition()
                                } else {
                                    onRequestMicrophonePermission()
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(44.dp)
                                .background(
                                    if (isListening) DangerRed else SurfaceVariantDark,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Pesquisar por voz em pt-BR",
                                tint = if (isListening) Color.White else PrimaryYellow
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = PrimaryYellow,
                    unfocusedBorderColor = SurfaceVariantDark,
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                )
            )

            // Aviso de status ou mensagem amigável de voz
            AnimatedVisibility(
                visible = statusNotice != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                statusNotice?.let { notice ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceVariantDark,
                        border = BorderStroke(1.dp, PrimaryYellow.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextLight),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearStatusNotice() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Fechar aviso",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Consultados Recentemente
            if (recentProducts.isNotEmpty() && searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CodeHighlight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSULTADOS RECENTEMENTE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CodeHighlight,
                            letterSpacing = 1.sp
                        )
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(recentProducts) { recent ->
                        RecentProductChip(
                            recent = recent,
                            onClick = { viewModel.setSearchQuery(recent.code) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de Produtos
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Nenhum produto cadastrado" else "Nenhum produto encontrado para \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextLight, fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Cadastre seu primeiro produto ou importe uma planilha CSV." else "Verifique o termo digitado ou cadastre um novo produto com este código.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.openAddDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = BackgroundDark)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cadastrar Produto", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { importCsvLauncher.launch("text/*") }
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = CodeHighlight)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Importar CSV", color = CodeHighlight)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            onCardClick = { viewModel.openSpotlight(product) },
                            onCopyClick = { copyCode(product.code, product.name) },
                            onEditClick = { viewModel.openEditDialog(product) },
                            onDeleteClick = { viewModel.requestDeleteConfirmation(product) }
                        )
                    }
                }
            }
        }
    }

    // Modal de Destaque do Código (Números Gigantes para Balança)
    selectedSpotlight?.let { product ->
        SpotlightDialog(
            product = product,
            onDismiss = { viewModel.closeSpotlight() },
            onCopy = { copyCode(product.code, product.name) }
        )
    }

    // Modal de Cadastro / Edição
    if (isAddEditDialogOpen) {
        ProductFormDialog(
            productToEdit = productToEdit,
            errorMessage = formErrorMessage,
            onDismiss = { viewModel.closeAddEditDialog() },
            onSave = { name, code, imageUri, price, unit, isAvailable ->
                viewModel.saveProduct(name, code, imageUri, price, unit, isAvailable)
            }
        )
    }

    // Modal de Importar / Exportar Dados
    if (isDataBackupDialogOpen) {
        DataBackupDialog(
            productsCount = products.size,
            onDismiss = { isDataBackupDialogOpen = false },
            onExportCsv = { exportCsvLauncher.launch("produtos_balanca.csv") },
            onImportCsv = { importCsvLauncher.launch("text/*") },
            onExportZip = { exportZipLauncher.launch("backup_balanca.zip") },
            onImportZip = { importZipLauncher.launch("application/zip") }
        )
    }

    // Diálogo de Confirmação de Exclusão
    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteConfirmation() },
            title = {
                Text(
                    text = "Confirmar exclusão?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextLight)
                )
            },
            text = {
                Text(
                    text = "Deseja realmente remover o produto \"${product.name}\" (Código: ${product.code})?",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelDeleteConfirmation() }) {
                    Text("Cancelar", color = TextLight)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Chip para produto consultado recentemente
 */
@Composable
fun RecentProductChip(
    recent: RecentProduct,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, SurfaceVariantDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = recent.code,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryYellow
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = recent.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextLight,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Card do produto na lista com renderização persistente de imagem do armazenamento interno
 */
@Composable
fun ProductCard(
    product: Product,
    onCardClick: () -> Unit,
    onCopyClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() },
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SurfaceVariantDark)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 540.dp

            if (isCompact) {
                // SMARTPHONE NA ORIENTAÇÃO VERTICAL (PORTRAIT: 320dp - 430dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 2. FOTO QUADRADA PROPORCIONAL: 64dp carregada com persistência interna
                    val bitmap = remember(product.imageUri) {
                        ImageHelper.loadThumbnail(product.imageUri, 160)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BackgroundDark,
                        border = BorderStroke(1.dp, SurfaceVariantDark),
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Foto do produto",
                                    tint = PrimaryYellow,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // COLUNA CENTRAL: HIERARQUIA VISUAL
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // 1. CÓDIGO DO PRODUTO: MAIOR DESTAQUE VISUAL
                        Text(
                            text = product.code,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = PrimaryYellow,
                                letterSpacing = 1.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // 3. NOME COMPLETO: LEGÍVEL E COM QUEBRA AUTOMÁTICA DE LINHA
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                lineHeight = 20.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 4. STATUS DISPONÍVEL OU ESGOTADO: SEMPRE VISÍVEL
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (product.isAvailable) AccentGreen.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (product.isAvailable) AccentGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (product.isAvailable) AccentGreen else DangerRed)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (product.isAvailable) "Disponível" else "Esgotado",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (product.isAvailable) AccentGreen else DangerRed
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 5. PREÇO E UNIDADE JUNTOS NA MESMA LINHA: R$ 8,99 / kg
                        if (!product.price.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BackgroundDark.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, SurfaceVariantDark)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "R$ ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGreen
                                        )
                                    )
                                    Text(
                                        text = product.price,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = AccentGreen
                                        )
                                    )
                                    Text(
                                        text = "/${product.unit}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // AÇÕES LATERAIS NO CELULAR
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        IconButton(
                            onClick = onCopyClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar Código",
                                tint = CodeHighlight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir",
                                tint = DangerRed.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // TABLET E SMARTPHONE HORIZONTAL (>= 540dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabletBitmap = remember(product.imageUri) {
                        ImageHelper.loadThumbnail(product.imageUri, 140)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BackgroundDark,
                        border = BorderStroke(1.dp, SurfaceVariantDark),
                        modifier = Modifier.size(50.dp)
                    ) {
                        if (tabletBitmap != null) {
                            Image(
                                bitmap = tabletBitmap.asImageBitmap(),
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Foto do produto",
                                    tint = PrimaryYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Nome do Produto, Status e Preço
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (product.isAvailable) AccentGreen.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, if (product.isAvailable) AccentGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (product.isAvailable) "Disponível" else "Esgotado",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (product.isAvailable) AccentGreen else DangerRed
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!product.price.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "R$ ${product.price}/${product.unit}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentGreen
                                )
                            )
                        }
                    }

                    // Código em NÚMEROS GRANDES
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(2.dp, PrimaryYellow.copy(alpha = 0.8f)),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = product.code,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryYellow,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Column {
                            IconButton(
                                onClick = onCopyClick,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar Código",
                                    tint = CodeHighlight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = onEditClick,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar",
                                        tint = TextMuted,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onDeleteClick,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir",
                                        tint = DangerRed.copy(alpha = 0.8f),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Destaque Colossal para o operador da balança
 */
@Composable
fun SpotlightDialog(
    product: Product,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceDark,
            border = BorderStroke(2.dp, PrimaryYellow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Foto no destaque se disponível
                val photo = remember(product.imageUri) {
                    ImageHelper.loadThumbnail(product.imageUri, 240)
                }
                if (photo != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SurfaceVariantDark),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "CÓDIGO DE BALANÇA",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                // NÚMERO COLOSSAL
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BackgroundDark,
                    border = BorderStroke(3.dp, PrimaryYellow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = product.code,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = PrimaryYellow,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Fechar", color = TextLight, fontSize = 16.sp)
                    }

                    Button(
                        onClick = {
                            onCopy()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = BackgroundDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copiar Código",
                            color = BackgroundDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Formulário para Adicionar ou Editar Produto com persistência interna de imagem
 */
@Composable
fun ProductFormDialog(
    productToEdit: Product?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String, imageUri: String?, price: String?, unit: String, isAvailable: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var code by remember { mutableStateOf(productToEdit?.code ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price ?: "") }
    var unit by remember { mutableStateOf(productToEdit?.unit ?: "kg") }
    var isAvailable by remember { mutableStateOf(productToEdit?.isAvailable ?: true) }
    var imageUri by remember { mutableStateOf(productToEdit?.imageUri) }

    // Salva a foto imediatamente no armazenamento interno permanente ao selecionar da galeria
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val savedPath = ImageHelper.saveImageFromUri(context, it, code.ifBlank { "novo" })
            if (savedPath != null) {
                imageUri = savedPath
                Toast.makeText(context, "Foto salva com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Erro ao processar imagem da galeria", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (productToEdit != null) "Editar Produto" else "Adicionar Novo Produto",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryYellow
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Seletor de Disponibilidade: Disponível ou Esgotado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isAvailable = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAvailable) AccentGreen else SurfaceVariantDark,
                            contentColor = if (isAvailable) Color.White else TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Disponível", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { isAvailable = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isAvailable) DangerRed else SurfaceVariantDark,
                            contentColor = if (!isAvailable) Color.White else TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Esgotado", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Área da Imagem: Prévia persistente ou botão para adicionar
                val previewBitmap = remember(imageUri) {
                    ImageHelper.loadThumbnail(imageUri, 180)
                }

                if (previewBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BackgroundDark, shape = RoundedCornerShape(12.dp))
                            .border(1.dp, SurfaceVariantDark, shape = RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Prévia da foto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Foto vinculada ao produto",
                                fontSize = 12.sp,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Trocar",
                                    fontSize = 12.sp,
                                    color = PrimaryYellow,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { photoPickerLauncher.launch("image/*") }
                                )
                                Text(
                                    text = "Remover",
                                    fontSize = 12.sp,
                                    color = DangerRed,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { imageUri = "" }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Selecionar Foto",
                            tint = PrimaryYellow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Adicionar Foto da Galeria",
                            color = TextLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Campo Nome
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Produto (ex: Maçã Fuji)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryYellow,
                        focusedLabelColor = PrimaryYellow,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo Código (Tratado como texto para preservar zeros à esquerda!)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código da Balança (ex: 102 ou 005)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryYellow,
                        focusedLabelColor = PrimaryYellow,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Campo Preço
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Preço de Venda (R$ por $unit)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        focusedLabelColor = AccentGreen,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Unidade de Venda da Balança (UN, PCT, KG, 100G, G, BDJ)
                Text(
                    text = "Unidade de Venda da Balança",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextLight,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                val availableUnits = listOf("UN", "PCT", "KG", "100G", "G", "BDJ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableUnits.forEach { u ->
                        val isSelected = unit.equals(u, ignoreCase = true)
                        Surface(
                            onClick = { unit = u.lowercase() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PrimaryYellow else SurfaceVariantDark,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryYellow else SurfaceVariantDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = u,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) BackgroundDark else TextLight,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Mensagem de Erro / Duplicidade
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(color = DangerRed, fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name, code, imageUri, price.ifBlank { null }, unit, isAvailable) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                    ) {
                        Text(
                            text = if (productToEdit != null) "Atualizar" else "Salvar",
                            color = BackgroundDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo dedicado de Importação e Exportação de Dados e Backup
 */
@Composable
fun DataBackupDialog(
    productsCount: Int,
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, SurfaceVariantDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Importar / Exportar Dados",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryYellow
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Fechar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Seção CSV
                Text(
                    text = "ARQUIVO CSV (TABELA EXCEL)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Botão Exportar CSV
                Button(
                    onClick = {
                        onExportCsv()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exportar CSV ($productsCount produtos)",
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botão Importar CSV
                Button(
                    onClick = {
                        onImportCsv()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = CodeHighlight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importar Planilha CSV",
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "* Na importação CSV, os produtos existentes são preservados e as fotos já cadastradas são mantidas.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextMuted),
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Seção Backup Completo com Fotos
                Text(
                    text = "BACKUP COMPLETO COM FOTOS (.ZIP)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExportZip()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrimaryYellow.copy(alpha = 0.6f))
                    ) {
                        Text("Exportar ZIP", color = PrimaryYellow, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onImportZip()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SurfaceVariantDark)
                    ) {
                        Text("Restaurar ZIP", color = TextLight, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar", color = TextMuted)
                }
            }
        }
    }
}
