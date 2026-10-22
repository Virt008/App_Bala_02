package com.balanca.assistente.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.balanca.assistente.data.AppDatabase
import com.balanca.assistente.data.Product
import com.balanca.assistente.data.RecentProduct
import com.balanca.assistente.util.CsvHelper
import com.balanca.assistente.util.ImageHelper
import com.balanca.assistente.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Locale

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val dao = db.productDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allProducts = dao.getAllProducts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Filtro em tempo real sem precisar teclar Enter
    val filteredProducts: StateFlow<List<Product>> = combine(allProducts, _searchQuery) { products, query ->
        if (query.isBlank()) {
            products
        } else {
            products.filter { product ->
                TextNormalizer.matches(product.name, product.code, query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentProducts: StateFlow<List<RecentProduct>> = dao.getRecentProducts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Modais e Estados de UI
    private val _selectedSpotlightProduct = MutableStateFlow<Product?>(null)
    val selectedSpotlightProduct: StateFlow<Product?> = _selectedSpotlightProduct.asStateFlow()

    private val _isAddEditDialogOpen = MutableStateFlow(false)
    val isAddEditDialogOpen: StateFlow<Boolean> = _isAddEditDialogOpen.asStateFlow()

    private val _productToEdit = MutableStateFlow<Product?>(null)
    val productToEdit: StateFlow<Product?> = _productToEdit.asStateFlow()

    private val _productToDelete = MutableStateFlow<Product?>(null)
    val productToDelete: StateFlow<Product?> = _productToDelete.asStateFlow()

    private val _formErrorMessage = MutableStateFlow<String?>(null)
    val formErrorMessage: StateFlow<String?> = _formErrorMessage.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    // Reconhecimento de Voz
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    fun openSpotlight(product: Product) {
        _selectedSpotlightProduct.value = product
        // Salva na lista de recentes automaticamente
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRecent(
                RecentProduct(
                    code = product.code,
                    name = product.name,
                    accessedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun closeSpotlight() {
        _selectedSpotlightProduct.value = null
    }

    fun openAddDialog() {
        _productToEdit.value = null
        _formErrorMessage.value = null
        _isAddEditDialogOpen.value = true
    }

    fun openEditDialog(product: Product) {
        _productToEdit.value = product
        _formErrorMessage.value = null
        _isAddEditDialogOpen.value = true
    }

    fun closeAddEditDialog() {
        _isAddEditDialogOpen.value = false
        _productToEdit.value = null
        _formErrorMessage.value = null
    }

    fun requestDeleteConfirmation(product: Product) {
        _productToDelete.value = product
    }

    fun cancelDeleteConfirmation() {
        _productToDelete.value = null
    }

    fun confirmDelete() {
        val product = _productToDelete.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ImageHelper.deleteImageFile(product.imageUri)
            dao.delete(product)
            dao.deleteRecentByCode(product.code)
            _productToDelete.value = null
            _statusNotice.value = "Produto \"${product.name}\" excluído com sucesso."
        }
    }

    /**
     * Salva novo produto ou atualiza existente.
     * Retorna falso se houver duplicidade de código.
     */
    fun saveProduct(
        name: String,
        code: String,
        imageUri: String? = null,
        price: String? = null,
        unit: String = "kg",
        isAvailable: Boolean = true
    ): Boolean {
        val cleanName = name.trim()
        val cleanCode = code.trim()

        if (cleanName.isEmpty() || cleanCode.isEmpty()) {
            _formErrorMessage.value = "Preencha o nome e o código do produto."
            return false
        }

        val currentEdit = _productToEdit.value

        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getByCode(cleanCode)
            if (existing != null && (currentEdit == null || existing.id != currentEdit.id)) {
                _formErrorMessage.value = "Atenção: O código \"$cleanCode\" já está cadastrado para o produto \"${existing.name}\"."
                return@launch
            }

            if (currentEdit != null) {
                // Atualização: Se imageUri for string vazia "", significa que foi removida
                val finalImageUri = when {
                    imageUri == "" -> null
                    imageUri != null -> imageUri
                    else -> currentEdit.imageUri
                }

                // Se a foto anterior mudou ou foi removida, exclui o arquivo antigo para economizar espaço
                if (currentEdit.imageUri != null && currentEdit.imageUri != finalImageUri) {
                    ImageHelper.deleteImageFile(currentEdit.imageUri)
                }

                val updated = currentEdit.copy(
                    name = cleanName,
                    normalizedName = TextNormalizer.normalize(cleanName),
                    code = cleanCode,
                    imageUri = finalImageUri,
                    price = price ?: currentEdit.price,
                    unit = unit,
                    isAvailable = isAvailable,
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updated)
                _statusNotice.value = "Produto \"$cleanName\" atualizado."
            } else {
                // Novo cadastro
                val finalImageUri = if (imageUri.isNullOrEmpty()) null else imageUri
                val newProduct = Product(
                    name = cleanName,
                    normalizedName = TextNormalizer.normalize(cleanName),
                    code = cleanCode,
                    imageUri = finalImageUri,
                    price = price,
                    unit = unit,
                    isAvailable = isAvailable
                )
                dao.insert(newProduct)
                _statusNotice.value = "Produto \"$cleanName\" adicionado com sucesso."
            }

            _isAddEditDialogOpen.value = false
            _productToEdit.value = null
            _formErrorMessage.value = null
            _searchQuery.value = ""
        }

        return true
    }

    fun clearStatusNotice() {
        _statusNotice.value = null
    }

    // Exportação CSV
    fun getCsvContent(): String {
        return CsvHelper.exportToCsv(allProducts.value)
    }

    // Importação CSV com suporte a todos os campos
    fun importFromCsv(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parseResult = CsvHelper.parseCsv(inputStream)
                val list = parseResult.validProducts
                if (list.isEmpty()) {
                    _statusNotice.value = "Nenhum produto válido encontrado no arquivo CSV."
                    return@launch
                }
                var importedCount = 0
                for (p in list) {
                    val existing = dao.getByCode(p.code)
                    if (existing != null) {
                        dao.update(
                            existing.copy(
                                name = p.name,
                                normalizedName = p.normalizedName,
                                price = p.price ?: existing.price,
                                unit = p.unit,
                                isAvailable = p.isAvailable,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.insert(p)
                    }
                    importedCount++
                }
                _statusNotice.value = "$importedCount produtos importados com sucesso do CSV."
            } catch (e: Exception) {
                _statusNotice.value = "Erro ao importar CSV: ${e.localizedMessage}"
            }
        }
    }

    // Exportação Backup ZIP Completo (com fotos)
    fun exportZip(context: Context, outputStream: java.io.OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CsvHelper.exportZipBackup(allProducts.value, context, outputStream)
                _statusNotice.value = "Backup completo ZIP exportado com sucesso."
            } catch (e: Exception) {
                _statusNotice.value = "Erro ao exportar backup ZIP: ${e.localizedMessage}"
            }
        }
    }

    // Restauração Backup ZIP Completo (com fotos)
    fun importZip(context: Context, inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = CsvHelper.importZipBackup(inputStream, context)
                if (list.isEmpty()) {
                    _statusNotice.value = "Nenhum produto válido encontrado no backup ZIP."
                    return@launch
                }
                var count = 0
                for (p in list) {
                    val existing = dao.getByCode(p.code)
                    if (existing != null) {
                        dao.update(
                            existing.copy(
                                name = p.name,
                                normalizedName = p.normalizedName,
                                price = p.price ?: existing.price,
                                unit = p.unit,
                                isAvailable = p.isAvailable,
                                imageUri = p.imageUri ?: existing.imageUri,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.insert(p)
                    }
                    count++
                }
                _statusNotice.value = "$count produtos e fotos restaurados do backup ZIP."
            } catch (e: Exception) {
                _statusNotice.value = "Erro ao restaurar backup ZIP: ${e.localizedMessage}"
            }
        }
    }

    // Reconhecimento de Voz em Português do Brasil com tratamento completo de erros
    fun toggleVoiceRecognition(context: Context) {
        if (_isListening.value) {
            stopVoiceRecognition()
        } else {
            startVoiceRecognition(context)
        }
    }

    fun onMicrophonePermissionDenied() {
        _isListening.value = false
        _statusNotice.value = "Permissão de gravação de áudio negada. Autorize o microfone nas configurações do aparelho."
    }

    fun startVoiceRecognition(context: Context) {
        if (_isListening.value) {
            stopVoiceRecognition()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _statusNotice.value = "Reconhecimento de voz indisponível neste aparelho. Por favor, utilize a busca digitada."
            return
        }

        try {
            // Cancela e destrói qualquer sessão anterior para evitar sessões simultâneas (erro 8 / erro 5)
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> // 3
                            "Erro na captura de áudio. Verifique se o microfone está funcionando."
                        SpeechRecognizer.ERROR_CLIENT -> // 5
                            "O serviço de voz do Google não pôde ser iniciado. Verifique se o aplicativo do Google está instalado e com as permissões ativas."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> // 9
                            "Permissão de gravação de áudio negada. Autorize o acesso ao microfone."
                        SpeechRecognizer.ERROR_NETWORK -> // 2
                            "Sem conexão com a internet. Para pesquisa por voz sem internet, instale o pacote de voz em Português no menu do Android (Configurações > Google > Reconhecimento de voz offline)."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> // 1
                            "Tempo de conexão esgotado. Verifique sua conexão à internet e tente novamente."
                        SpeechRecognizer.ERROR_NO_MATCH -> // 7
                            "Nenhuma palavra identificada. Fale mais perto do microfone e tente novamente."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> // 8
                            "O serviço de voz está ocupado no momento. Aguarde alguns instantes."
                        SpeechRecognizer.ERROR_SERVER -> // 4
                            "Erro no servidor de reconhecimento de voz. Tente novamente mais tarde."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> // 6
                            "Nenhuma fala foi detectada. Toque no microfone e diga o nome ou código do produto."
                        10 -> // ERROR_TOO_MANY_REQUESTS
                            "Muitas tentativas simultâneas. Aguarde um instante."
                        11 -> // ERROR_SERVER_DISCONNECTED
                            "Conexão com o servidor de voz interrompida."
                        12 -> // ERROR_LANGUAGE_NOT_SUPPORTED
                            "O pacote de voz offline em Português (pt-BR) não está instalado no aparelho. Conecte-se à internet ou instale o pacote de voz nas configurações do Android."
                        13 -> // ERROR_LANGUAGE_UNAVAILABLE
                            "Idioma Português (pt-BR) temporariamente indisponível no reconhecimento de voz."
                        14 -> // ERROR_CANNOT_CHECK_SUPPORT
                            "Não foi possível verificar o suporte de voz no aparelho."
                        else ->
                            "Não foi possível captar a voz. Digite o nome ou código no campo de busca."
                    }
                    _statusNotice.value = errorMsg
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val heard = matches[0].trim()
                        if (heard.isNotEmpty()) {
                            _searchQuery.value = heard
                            _statusNotice.value = "Pesquisando por: \"$heard\""
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _statusNotice.value = "Não foi possível iniciar o microfone. A busca digitada continua 100% ativa."
        }
    }

    fun stopVoiceRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        _isListening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
