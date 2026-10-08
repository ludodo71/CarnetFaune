package fr.carnetfaune.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButton
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.compose.AsyncImage
import fr.carnetfaune.app.data.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var shortcutDestination: String = "carnet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutDestination = savedInstanceState?.getString("shortcutDestination") ?: destinationFrom(intent)
        val migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE places ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE places ADD COLUMN habitat TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN temperatureC REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN weather TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN habitat TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN behavior TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN sex TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN lifeStage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE observations ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE observations ADD COLUMN photoUri TEXT")
            }
        }
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "carnet-faune.db").addMigrations(migration).build()
        setContent { CarnetFauneApp(AppRepository(db), shortcutDestination) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutDestination = destinationFrom(intent)
        recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("shortcutDestination", shortcutDestination)
        super.onSaveInstanceState(outState)
    }

    private fun destinationFrom(intent: Intent?): String = when (intent?.data?.host) {
        "new-observation" -> "carnet"
        "map" -> "map"
        "statistics" -> "statistics"
        else -> "carnet"
    }
}

class MainVm(private val repo: AppRepository) : ViewModel() {
    val species = repo.species
    val places = repo.places
    val observations = repo.observations
    var syncing by mutableStateOf(false); private set
    var syncMessage by mutableStateOf(""); private set
    fun addPlace(name:String,lat:Double?,lon:Double?,habitat:String)=viewModelScope.launch{repo.addPlace(name,lat,lon,habitat)}
    fun deletePlace(p:Place)=viewModelScope.launch{repo.deletePlace(p)}
    fun addObservation(o:Observation)=viewModelScope.launch{repo.addObservation(o)}
    fun deleteObservation(o:Observation)=viewModelScope.launch{repo.deleteObservation(o)}
    fun sync()=viewModelScope.launch{syncing=true;runCatching{repo.syncCatalog{syncMessage=it}}.onFailure{syncMessage="Erreur : ${it.message ?: "réseau indisponible"}"};syncing=false}
    fun image(id:Long,onResult:(String?)->Unit)=viewModelScope.launch{onResult(repo.firstImageUrl(id))}
    suspend fun exportData():List<ObservationExport>{
        val placesNow=repo.placesSnapshot(); val speciesNow=repo.speciesSnapshot(); val obs=repo.snapshot()
        return obs.mapNotNull{o->
            val s=speciesNow.firstOrNull{it.id==o.speciesId}?:return@mapNotNull null
            val p=placesNow.firstOrNull{it.id==o.placeId}?:return@mapNotNull null
            ObservationExport(o,s,p)
        }
    }
}

data class ObservationExport(val o:Observation,val s:Species,val p:Place)

// Snapshot helpers kept local to the repository through Flow-first UI; used only by export.
// Line 134-135
suspend fun AppRepository.placesSnapshot(): List<Place> = places.first()
suspend fun AppRepository.speciesSnapshot(): List<Species> = species.first()

@Composable fun CarnetFauneApp(vmRepo:AppRepository, initialDestination:String = "carnet"){
    val vm=remember{MainVm(vmRepo)}
    var tab by remember(initialDestination){mutableIntStateOf(when(initialDestination){"statistics"->1;"map"->2;else->0})}
    Scaffold(bottomBar={NavigationBar{
        NavigationBarItem(tab==0,{tab=0},{Icon(Icons.Default.TableChart,null)},label={Text("Carnet")})
        NavigationBarItem(tab==1,{tab=1},{Icon(Icons.Default.BarChart,null)},label={Text("Analyses")})
        NavigationBarItem(tab==2,{tab=2},{Icon(Icons.Default.Map,null)},label={Text("Carte")})
        NavigationBarItem(tab==3,{tab=3},{Icon(Icons.Default.CalendarMonth,null)},label={Text("Calendrier")})
        NavigationBarItem(tab==4,{tab=4},{Icon(Icons.Default.PhotoLibrary,null)},label={Text("Photos")})
        NavigationBarItem(tab==5,{tab=5},{Icon(Icons.Default.Info,null)},label={Text("Espèces")})
    }}){pad->Box(Modifier.padding(pad).fillMaxSize()){when(tab){0->ObservationSheet(vm);1->Charts(vm);2->MapScreen(vm);3->CalendarScreen(vm);4->GalleryScreen(vm);5->CatalogScreen(vm)}}}
}

@Composable
private fun ObservationSheet(vm: MainVm) {

    val species by vm.species.collectAsStateWithLifecycle(emptyList())
    val places by vm.places.collectAsStateWithLifecycle(emptyList())
    val obs by vm.observations.collectAsStateWithLifecycle(emptyList())

    var search by remember {
        mutableStateOf("")
    }

    var group by remember {
        mutableStateOf("TOUS")
    }

    var placeDialog by remember {
        mutableStateOf(false)
    }

    var entry by remember {
        mutableStateOf<Pair<Species, Place>?>(null)
    }

    var history by remember {
        mutableStateOf<Pair<Species, Place>?>(null)
    }

    val filtered = species.filter {
        (group == "TOUS" || it.group == group) &&
            (
                search.isBlank() ||
                    it.commonName.contains(search, true) ||
                    it.scientificName.contains(search, true)
            )
    }

    Column(
        Modifier.fillMaxSize()
    ) {

        // ─────────────────────────────
        // EN-TÊTE
        // ─────────────────────────────

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    "Carnet d'observations",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "${obs.size} observation(s) enregistrée(s)",
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    placeDialog = true
                }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Ajouter un lieu"
                )
            }

            ExportButton(vm)
        }

        // ─────────────────────────────
        // RECHERCHE
        // ─────────────────────────────

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
            },
            label = {
                Text("Rechercher une espèce")
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        // ─────────────────────────────
        // FILTRES
        // ─────────────────────────────

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            listOf(
                "TOUS",
                "OISEAUX",
                "MAMMIFÈRES",
                "RONGEURS",
                "REPTILES"
            ).forEach { g ->

                FilterChip(
                    selected = g == group,
                    onClick = {
                        group = g
                    },
                    label = {
                        Text(g)
                    }
                )
            }
        }

        // ─────────────────────────────
        // AUCUN LIEU
        // ─────────────────────────────

        if (places.isEmpty()) {

            EmptyPlaces {
                placeDialog = true
            }

        } else {

            // ─────────────────────────
            // LISTE DES LIEUX
            // ─────────────────────────

            LazyColumn(
                Modifier.fillMaxSize()
            ) {

                items(filtered) { s ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            )
                    ) {

                        Column(
                            Modifier.padding(10.dp)
                        ) {

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Column(
                                    Modifier.weight(1f)
                                ) {

                                    Text(
                                        s.commonName,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleMedium
                                    )

                                    Text(
                                        s.scientificName,
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        s.group,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            // ─────────────────
                            // LIEUX
                            // ─────────────────

                            places.forEach { p ->

                                val matches =
                                    obs.filter {
                                        it.speciesId == s.id &&
                                            it.placeId == p.id
                                    }

                                val total =
                                    matches.sumOf {
                                        it.count
                                    }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 3.dp
                                        ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Column(
                                        Modifier.weight(1f)
                                    ) {

                                        Text(
                                            p.name,
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .labelLarge
                                        )

                                        if (total > 0) {

                                            Text(
                                                "$total individu(s) observé(s)",
                                                fontSize = 11.sp
                                            )
                                        } else {

                                            Text(
                                                "Aucune observation",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            entry = s to p
                                        }
                                    ) {

                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null
                                        )

                                        Spacer(
                                            Modifier.width(4.dp)
                                        )

                                        Text("Observer")
                                    }

                                    if (matches.isNotEmpty()) {

                                        TextButton(
                                            onClick = {
                                                history = s to p
                                            }
                                        ) {

                                            Text("Historique")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────
    // DIALOGUE : NOUVEAU LIEU
    // ─────────────────────────────────

    if (placeDialog) {

        AddPlaceDialog(
            onAdd = { name, lat, lon, habitat ->

                vm.addPlace(
                    name,
                    lat,
                    lon,
                    habitat
                )

                placeDialog = false
            },
            onCancel = {
                placeDialog = false
            }
        )
    }

    // ─────────────────────────────────
    // DIALOGUE : NOUVELLE OBSERVATION
    // ─────────────────────────────────

    entry?.let { (s, p) ->

        ObservationDialog(
            s = s,
            p = p,
            onSave = { observation ->

                vm.addObservation(observation)

                entry = null
            },
            onCancel = {

                entry = null
            }
        )
    }

    // ─────────────────────────────────
    // HISTORIQUE
    // ─────────────────────────────────

    history?.let { (s, p) ->

        HistoryDialog(
            s = s,
            p = p,
            items = obs.filter {
                it.speciesId == s.id &&
                    it.placeId == p.id
            },
            onCancel = {
                history = null
            },
            vm = vm
        )
    }
}

@Composable
private fun ExportButton(vm: MainVm) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    IconButton(onClick = {
        scope.launch {
            val rows = vm.exportData()

            val header =
                "date;heure;espèce;nom_scientifique;groupe;lieu;nombre;température;meteo;habitat;comportement;sexe;stade;latitude;longitude;notes"

            val csv = buildString {
                appendLine(header)

                rows.forEach { r ->
                    val o = r.o

                    val line = listOf(
                        o.date,
                        o.time,
                        r.s.commonName,
                        r.s.scientificName,
                        r.s.group,
                        r.p.name,
                        o.count,
                        o.temperatureC ?: "",
                        o.weather,
                        o.habitat,
                        o.behavior,
                        o.sex,
                        o.lifeStage,
                        o.latitude ?: r.p.latitude ?: "",
                        o.longitude ?: r.p.longitude ?: "",
                        o.note
                    ).joinToString(";") {
                        it.toString()
                            .replace(";", ",")
                            .replace("\n", " ")
                    }

                    appendLine(line)
                }
            }

            val file = File(
                context.cacheDir,
                "carnet-faune-export.csv"
            )

            file.writeText("\uFEFF$csv")

            val uri = FileProvider.getUriForFile(
                context,
                "fr.carnetfaune.app.fileprovider",
                file
            )

            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Exporter les observations"
                )
            )
        }
    }) {
        Icon(
            Icons.Default.FileDownload,
            "Exporter CSV"
        )
    }
}

@Composable private fun HeaderCell(text:String){Box(Modifier.height(48.dp).fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).border(.5.dp,MaterialTheme.colorScheme.outlineVariant),contentAlignment=Alignment.CenterStart){Text(text,Modifier.padding(8.dp),fontSize=12.sp)}}
@Composable private fun SpeciesRow(s:Species,vm:MainVm){var image by remember(s.id){mutableStateOf<String?>(s.imageUrl)};LaunchedEffect(s.id){if(image==null)vm.image(s.id){image=it}};Row(Modifier.height(64.dp).fillMaxWidth().border(.5.dp,MaterialTheme.colorScheme.outlineVariant).padding(5.dp),verticalAlignment=Alignment.CenterVertically){if(image!=null)AsyncImage(model=image,contentDescription=s.commonName,modifier=Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))else Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(6.dp)));Column(Modifier.padding(start=8.dp)){Text(s.commonName,maxLines=1);Text(s.scientificName,fontSize=11.sp,maxLines=1)}}}
@Composable private fun EmptyPlaces(onAdd:()->Unit){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("Crée ton premier lieu d'observation");Spacer(Modifier.height(12.dp));Button(onClick=onAdd){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Ajouter un lieu")}}}

@Composable
private fun AddPlaceDialog(
    onAdd: (String, Double?, Double?, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var habitat by remember { mutableStateOf("") }

    val permission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                readLastLocation(context) { latitude, longitude ->
                    lat = latitude.toString()
                    lon = longitude.toString()
                }
            }
        }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Nouveau lieu")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du lieu") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude (facultatif)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude (facultatif)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = habitat,
                    onValueChange = { habitat = it },
                    label = { Text("Habitat principal") },
                    singleLine = true
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                TextButton(
                    onClick = {
                        val fineGranted =
                            ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                        val coarseGranted =
                            ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                        if (!fineGranted && !coarseGranted) {
                            permission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            readLastLocation(context) { latitude, longitude ->
                                lat = latitude.toString()
                                lon = longitude.toString()
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.width(4.dp)
                    )

                    Text("Utiliser ma position actuelle")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onAdd(
                        name,
                        lat.toDoubleOrNull(),
                        lon.toDoubleOrNull(),
                        habitat
                    )
                }
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Annuler")
            }
        }
    )
}

private fun readLastLocation(context:Context,onResult:(Double,Double)->Unit){
    val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if(ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
    val locations=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).mapNotNull{provider->runCatching{manager.getLastKnownLocation(provider)}.getOrNull()}
    locations.maxByOrNull{it.time}?.let{onResult(it.latitude,it.longitude)}
}

@Composable
private fun ObservationDialog(
    s: Species,
    p: Place,
    onSave: (Observation) -> Unit,
    onCancel: () -> Unit
) {
    var time by remember {
        mutableStateOf(
            LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm")
            )
        )
    }

    var date by remember {
        mutableStateOf(LocalDate.now().toString())
    }

    var count by remember {
        mutableIntStateOf(1)
    }

    var note by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf("") }
    var habitat by remember { mutableStateOf(p.habitat) }
    var behavior by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photo = uri?.toString()
    }

    AlertDialog(
        onDismissRequest = onCancel,

        title = {
            Text("Observation — " + s.commonName)
        },

        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Text(
                    p.name,
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Heure") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = count.toString(),
                    onValueChange = {
                        count =
                            it.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    },
                    label = {
                        Text("Nombre d'individus")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = temp,
                    onValueChange = { temp = it },
                    label = {
                        Text("Température °C")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = weather,
                    onValueChange = { weather = it },
                    label = {
                        Text("Météo")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = habitat,
                    onValueChange = { habitat = it },
                    label = {
                        Text("Habitat")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = behavior,
                    onValueChange = { behavior = it },
                    label = {
                        Text("Comportement")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = sex,
                    onValueChange = { sex = it },
                    label = {
                        Text("Sexe")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = stage,
                    onValueChange = { stage = it },
                    label = {
                        Text("Stade / âge")
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = {
                        Text("Notes")
                    },
                    minLines = 3
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            picker.launch("image/*")
                        }
                    ) {
                        Text(
                            if (photo == null)
                                "Ajouter une photo"
                            else
                                "Photo sélectionnée"
                        )
                    }
                }
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Observation(
                            speciesId = s.id,
                            placeId = p.id,
                            date = date,
                            time = time,
                            count = count,
                            note = note,
                            temperatureC = temp.toDoubleOrNull(),
                            weather = weather,
                            habitat = habitat,
                            behavior = behavior,
                            sex = sex,
                            lifeStage = stage,
                            latitude = p.latitude,
                            longitude = p.longitude,
                            photoUri = photo
                        )
                    )
                }
            ) {
                Text("Enregistrer")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Annuler")
            }
        }
    )
}

@Composable private fun HistoryDialog(s:Species,p:Place,items:List<Observation>,onCancel:()->Unit,vm:MainVm){AlertDialog(onDismissRequest=onCancel,title={Text("Historique — ${s.commonName}")},text={LazyColumn{items(items){o->Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){Text("${o.date} • ${o.time} • ${o.count} individu(s)",style=MaterialTheme.typography.labelLarge);if(o.weather.isNotBlank())Text("${o.weather}${o.temperatureC?.let{" • $it °C"}?:""}");if(o.behavior.isNotBlank())Text("Comportement : ${o.behavior}");if(o.note.isNotBlank())Text(o.note);if(o.photoUri!=null)AsyncImage(Uri.parse(o.photoUri),contentDescription=null,modifier=Modifier.size(70.dp).clip(RoundedCornerShape(6.dp)))}}}},confirmButton={TextButton(onClick=onCancel){Text("Fermer")}})}

@Composable private fun Charts(vm:MainVm){val places by vm.places.collectAsStateWithLifecycle(emptyList());val species by vm.species.collectAsStateWithLifecycle(emptyList());val obs by vm.observations.collectAsStateWithLifecycle(emptyList());var selected by remember{mutableStateOf<Long?>(null)};var selectedSpecies by remember{mutableStateOf<Long?>(null)};Column(Modifier.fillMaxSize().padding(16.dp)){Text("Analyses",style=MaterialTheme.typography.headlineSmall);Text("Les graphiques utilisent uniquement tes observations enregistrées.",fontSize=12.sp);Spacer(Modifier.height(12.dp));Text("Lieu",style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected==null,{selected=null},{Text("Tous")});places.forEach{FilterChip(selected==it.id,{selected=it.id},{Text(it.name)})}};Spacer(Modifier.height(8.dp));Text("Espèce",style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selectedSpecies==null,{selectedSpecies=null},{Text("Toutes")});species.take(30).forEach{FilterChip(selectedSpecies==it.id,{selectedSpecies=it.id},{Text(it.commonName)})}};Spacer(Modifier.height(16.dp));val data=IntArray(24);obs.filter{(selected==null||it.placeId==selected)&&(selectedSpecies==null||it.speciesId==selectedSpecies)}.forEach{it.time.take(2).toIntOrNull()?.let{h->if(h in 0..23)data[h]+=it.count}};HourChart(data);Spacer(Modifier.height(18.dp));Text("Total d'individus par heure",style=MaterialTheme.typography.titleMedium);Text("Tu peux comparer les lieux et les espèces pour repérer les périodes d'activité.",fontSize=12.sp)}}

@Composable private fun HourChart(data:IntArray){val max=(data.maxOrNull()?:1).coerceAtLeast(1);Row(Modifier.fillMaxWidth().height(230.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(2.dp)){data.forEachIndexed{h,v->Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){if(v>0)Text(v.toString(),fontSize=8.sp);Box(Modifier.fillMaxWidth().fillMaxHeight(.78f*(v.toFloat()/max).coerceAtLeast(.02f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart=3.dp,topEnd=3.dp)));Text(h.toString(),fontSize=8.sp)}}}}

@Composable
private fun CatalogScreen(vm: MainVm) {

    val species by vm.species.collectAsStateWithLifecycle(emptyList())

    var query by remember {
        mutableStateOf("")
    }

    var selected by remember {
        mutableStateOf<Species?>(null)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    "Catalogue naturaliste",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "TAXREF via GBIF • synchronisation en ligne",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = vm::sync,
                enabled = !vm.syncing
            ) {

                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null
                )

                Spacer(
                    Modifier.width(5.dp)
                )

                Text(
                    if (vm.syncing)
                        "Synchronisation…"
                    else
                        "Actualiser"
                )
            }
        }

        if (vm.syncMessage.isNotBlank()) {

            Text(
                vm.syncMessage,
                fontSize = 12.sp
            )
        }

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
            },
            label = {
                Text("Chercher une espèce")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "${species.size} taxons chargés",
            Modifier.padding(vertical = 8.dp)
        )

        LazyColumn {

            items(
                species
                    .filter {
                        query.isBlank() ||
                            it.commonName.contains(query, true) ||
                            it.scientificName.contains(query, true)
                    }
                    .take(500)
            ) { s ->

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = s
                        }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        s.commonName,
                        Modifier.weight(1f)
                    )

                    Text(
                        s.group,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    selected?.let { s ->

        SpeciesDetail(
            s,
            vm
        ) {
            selected = null
        }
    }
}
@Composable private fun SpeciesDetail(s:Species,vm:MainVm,onClose:()->Unit){
    var image by remember { mutableStateOf(s.imageUrl ?: "") }
    LaunchedEffect(s.id){
        if(image.isEmpty()) {
            vm.image(s.id){ newImage -> image = newImage ?: "" }
        }
    }
    // ... rest of the function
}

@Composable private fun MapScreen(vm:MainVm){
    val context=androidx.compose.ui.platform.LocalContext.current
    val places by vm.places.collectAsStateWithLifecycle(emptyList())
    val obs by vm.observations.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("Carte des observations",style=MaterialTheme.typography.headlineSmall)
        Text("Ouvre la carte Android centrée sur un lieu ou une observation GPS.",fontSize=12.sp)
        Spacer(Modifier.height(12.dp))
        if(places.none{it.latitude!=null&&it.longitude!=null} && obs.none{it.latitude!=null&&it.longitude!=null}) Text("Aucune coordonnée GPS enregistrée.")
        LazyColumn{items(places.filter{it.latitude!=null&&it.longitude!=null}){p->
            ListItem(headlineContent={Text(p.name)},supportingContent={Text("${p.latitude}, ${p.longitude}")},leadingContent={Icon(Icons.Default.Map,null)},modifier=Modifier.clickable{
                val uri=Uri.parse("geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}(${Uri.encode(p.name)})")
                context.startActivity(Intent(Intent.ACTION_VIEW,uri))
            })
        }}
    }
}

@Composable private fun CalendarScreen(vm:MainVm){
    val obs by vm.observations.collectAsStateWithLifecycle(emptyList())
    val species by vm.species.collectAsStateWithLifecycle(emptyList())
    val places by vm.places.collectAsStateWithLifecycle(emptyList())
    val grouped=obs.groupBy{it.date}.toSortedMap(compareByDescending{it})
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("Calendrier des observations",style=MaterialTheme.typography.headlineSmall)
        Text("${grouped.size} jour(s) avec observation",fontSize=12.sp)
        LazyColumn{grouped.forEach{(date,itemsForDate)->
            item{Text(date,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp,bottom=4.dp))}
            items(itemsForDate){o->
                val s=species.firstOrNull{it.id==o.speciesId}; val p=places.firstOrNull{it.id==o.placeId}
                ListItem(headlineContent={Text("${o.time} — ${s?.commonName ?: "Espèce"}")},supportingContent={Text("${p?.name ?: "Lieu"} • ${o.count} individu(s)")})
            }
        }}
    }
}

@Composable private fun GalleryScreen(vm:MainVm){
    val obs by vm.observations.collectAsStateWithLifecycle(emptyList())
    val species by vm.species.collectAsStateWithLifecycle(emptyList())
    val photos=obs.filter{!it.photoUri.isNullOrBlank()}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("Galerie naturaliste",style=MaterialTheme.typography.headlineSmall)
        Text("${photos.size} photo(s) associée(s) à tes observations",fontSize=12.sp)
        LazyColumn{items(photos){o->Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(Uri.parse(o.photoUri),species.firstOrNull{it.id==o.speciesId}?.commonName,Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)));Column(Modifier.padding(start=12.dp)){Text(species.firstOrNull{it.id==o.speciesId}?.commonName ?: "Observation");Text("${o.date} • ${o.time}",fontSize=12.sp);if(o.note.isNotBlank())Text(o.note,fontSize=12.sp)}}}}
    }
}
