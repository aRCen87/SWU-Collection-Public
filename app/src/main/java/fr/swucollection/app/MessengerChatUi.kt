package fr.swucollection.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun messengerCompactKeyboard():Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<480 && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
@Composable internal fun messengerContentPadding()=PaddingValues(horizontal=16.dp,vertical=if(messengerCompactKeyboard())0.dp else 10.dp)

@Composable
internal fun ColumnScope.CommunityChat(service: CommunityService, conversation: PlayerConversation, decks: List<DeckStore.SavedDeck>,
    catalog: List<CatalogCard>, onImportDeck: (String,PortableDeck)->Unit, peerName:String, onOpenCard:(SharedPlayerCard)->Unit, onBack:()->Unit) {
    var messages by remember(conversation.id){mutableStateOf<List<PlayerMessage>>(emptyList())}
    var draft by remember(conversation.id){mutableStateOf("")};var pendingId by remember(conversation.id){mutableStateOf(UUID.randomUUID().toString())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var outboundText by remember(conversation.id){mutableStateOf<String?>(null)}
    var outboundReply by remember(conversation.id){mutableStateOf<PlayerMessage?>(null)}
    var pendingDeck by remember(conversation.id){mutableStateOf<DeckStore.SavedDeck?>(null)}
    var share by remember{mutableStateOf(false)};var received by remember{mutableStateOf<PlayerMessage?>(null)}
    var reply by remember(conversation.id){mutableStateOf<PlayerMessage?>(null)}
    var stateLoaded by remember(conversation.id){mutableStateOf(false)}
    var state by remember(conversation.id){mutableStateOf(MessengerThreadState())}
    var preferences by remember{mutableStateOf(MessengerPreferences())};var signal by remember{mutableStateOf(MessengerSignal())}
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val chatContext=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope();val online=rememberInternetAvailable();val peer=conversation.members.firstOrNull{it!=service.uid}.orEmpty()
    DisposableEffect(service,conversation.id) {
        CommunityNotifications.activeThread=conversation.id
        val listeners=listOf(service.messages(conversation.id,{messages=it},{error=communityError(it)}),
            service.threadStates({state=it[conversation.id]?:MessengerThreadState();stateLoaded=true},{error=communityError(it)}),
            service.messengerPreferences({preferences=it},{error=communityError(it)}),service.signal(conversation.id,peer){signal=it})
        onDispose{CommunityNotifications.activeThread=null;listeners.forEach{it.remove()}}
    }
    LaunchedEffect(conversation.id,messages.lastOrNull()?.id,preferences.shareRead,lifecycleState) {
        if(messages.isNotEmpty() && lifecycleState==androidx.lifecycle.Lifecycle.State.RESUMED) {runCatching{service.markRead(conversation.id)};chatContext.getSystemService(android.app.NotificationManager::class.java).cancel("${service.uid}:message:${conversation.id}",1);runCatching{service.publishSignal(conversation.id,preferences.shareRead,false)}}
    }
    LaunchedEffect(conversation.id,draft,lifecycleState) {
        if(lifecycleState!=androidx.lifecycle.Lifecycle.State.RESUMED)return@LaunchedEffect
        delay(350)
        runCatching{service.publishSignal(conversation.id,preferences.shareRead,draft.isNotBlank())}
        if(draft.isNotBlank()) {delay(6000);runCatching{service.publishSignal(conversation.id,preferences.shareRead,false)}}
    }
    DisposableEffect(conversation.id) {onDispose{scope.launch{runCatching{service.publishSignal(conversation.id,preferences.shareRead,false)}}}}
    fun send(deck:DeckStore.SavedDeck?=null) {if(!busy && online)scope.launch {
        busy=true;error=null;if(outboundText==null){outboundText=draft;outboundReply=reply};if(deck!=null)pendingDeck=deck
        val sentDraft=outboundText.orEmpty();val sentReply=outboundReply
        try{withTimeout(20_000){service.sendRich(conversation.id,pendingId,sentDraft,pendingDeck,reply=sentReply)};if(draft.startsWith(sentDraft))draft=draft.removePrefix(sentDraft);pendingId=UUID.randomUUID().toString();if(reply?.id==sentReply?.id)reply=null;share=false;pendingDeck=null;outboundText=null;outboundReply=null}
        catch(e:CancellationException){if(e is TimeoutCancellationException)error="Envoi non confirmé. Réessaie : le message ne sera pas envoyé deux fois." else throw e}
        catch(e:Exception){error=communityError(e)}finally{busy=false;share=false}
    }}
    val shown=if(stateLoaded)messages.filter{it.createdAt>state.hiddenBefore && it.id !in state.hiddenMessages} else emptyList()
    MessengerChatLayout(service.uid,peerName,shown,draft,{draft=it.take(2000)},busy,online,error,signal,reply,
        catalog,{send()},{share=true},{reply=it},{reply=null},
        {message->scope.launch{runCatching{service.saveThreadState(conversation.id,state.copy(hiddenMessages=state.hiddenMessages+message.id))}.onFailure{error=communityError(it)}}},
        {message->if(message.kind=="deck")received=message else runCatching{SharedPlayerCard.parse(message.cardJson)}.onSuccess(onOpenCard).onFailure{error="Référence de carte indisponible."}},onBack=onBack)
    if(share) AlertDialog(onDismissRequest={if(!busy)share=false},title={Text("Partager un deck")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            if(decks.isEmpty())Text("Aucun deck enregistré.")
            decks.forEach {deck->OutlinedButton(onClick={send(deck)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(deck.name)}}
        }
    },confirmButton={TextButton(onClick={share=false},enabled=!busy){Text("Fermer")}})
    received?.let {message->
        val parsed=remember(message.id){runCatching{CommunityValidation.deck(message.deckJson)}.getOrNull()}
        AlertDialog(onDismissRequest={received=null},title={Text(message.deckName)},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(parsed==null)Text("Ce deck est invalide.") else {
                    Text("Une copie s’ouvrira dans Mes listes pour vérification avant enregistrement.")
                    (listOf(DeckEntry(parsed.leader,1),DeckEntry(parsed.base,1))+parsed.deck+parsed.sideboard).forEach {entry->
                        val card=catalog.firstOrNull{DeckConstruction.reference(it)==entry.cardId}
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            if(card!=null)CardArtwork(card,Modifier.size(35.dp,49.dp));Text("${entry.count} × ${card?.name?:entry.cardId}",fontSize=12.sp)
                        }
                    }
                }
            }
        },confirmButton={Button(onClick={if(parsed!=null){received=null;onImportDeck(message.deckName,parsed)}},enabled=parsed!=null){Text("Copier dans Mes listes")}},dismissButton={TextButton(onClick={received=null}){Text("Fermer")}})
    }
}

/** Fixed composer and a flexible message viewport: no screen-height fraction or outer scrolling. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.MessengerChatLayout(uid:String,peerName:String,messages:List<PlayerMessage>,draft:String,onDraft:(String)->Unit,
    busy:Boolean,online:Boolean,error:String?,signal:MessengerSignal,reply:PlayerMessage?,catalog:List<CatalogCard>,onSend:()->Unit,onShare:()->Unit,
    onReply:(PlayerMessage)->Unit,onCancelReply:()->Unit,onHide:(PlayerMessage)->Unit,onAttachment:(PlayerMessage)->Unit,onBack:(()->Unit)?=null) {
    val compactKeyboard=messengerCompactKeyboard()
    val scroll=rememberLazyListState();val clipboard=LocalClipboardManager.current
    var selected by remember{mutableStateOf<PlayerMessage?>(null)};var hide by remember{mutableStateOf<PlayerMessage?>(null)}
    var search by remember{mutableStateOf("")};var searching by remember{mutableStateOf(false)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){delay(5000);now=System.currentTimeMillis()}}
    LaunchedEffect(messages.lastOrNull()?.id,search,compactKeyboard){if(messages.isNotEmpty()){withFrameNanos { };scroll.scrollToItem((messages.count{search.isBlank() || it.text.contains(search,true) || it.deckName.contains(search,true)}-1).coerceAtLeast(0))}}
    if(!compactKeyboard)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        if(onBack!=null)IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Mes discussions")}
        Text(peerName,Modifier.weight(1f),fontWeight=FontWeight.Bold)
        IconButton(onClick={searching=!searching;if(!searching)search=""}){Icon(Icons.Default.Search,"Rechercher dans la discussion")}
    }
    if(searching && !compactKeyboard)OutlinedTextField(search,{search=it.take(80)},label={Text("Rechercher un message")},singleLine=true,modifier=Modifier.fillMaxWidth())
    val visible=messages.filter{search.isBlank() || it.text.contains(search,true) || it.deckName.contains(search,true)}
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("messenger_messages"),state=scroll,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=if(compactKeyboard)0.dp else 8.dp)) {
        if(visible.isEmpty())item{Text(if(search.isBlank())"Le début de votre discussion." else "Aucun message trouvé.",style=MaterialTheme.typography.bodySmall)}
        items(visible,key={it.id}){message->
            val own=message.senderId==uid
            Column(Modifier.fillMaxWidth(),horizontalAlignment=if(own)Alignment.End else Alignment.Start){
                Surface(Modifier.widthIn(max=360.dp).fillMaxWidth(.88f).combinedClickable(onClick={if(message.kind!="text")onAttachment(message)},onLongClick={selected=message}),
                    shape=RoundedCornerShape(18.dp),color=if(own)androidx.compose.ui.graphics.Color(0xFF1D4162) else PremiumNavy){
                    Column(Modifier.padding(horizontal=12.dp,vertical=if(compactKeyboard)4.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        if(message.replyText.isNotBlank() && !compactKeyboard)Text("↪ ${message.replyText}",fontSize=11.sp,color=PremiumGold)
                        if(message.text.isNotBlank() && (!compactKeyboard || message.kind=="text"))Text(message.text,maxLines=if(compactKeyboard)1 else Int.MAX_VALUE,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if(message.kind=="deck") {Text(message.deckName,fontWeight=FontWeight.Bold,maxLines=if(compactKeyboard)1 else Int.MAX_VALUE,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);if(!compactKeyboard)Text("Voir le deck ›",color=PremiumGold)}
                        if(message.kind=="card") {
                            val shared=remember(message.cardJson){runCatching{SharedPlayerCard.parse(message.cardJson)}.getOrNull()}
                            val card=remember(shared,catalog){catalog.firstOrNull{it.setCode==shared?.set && it.number.toIntOrNull()==shared?.number?.toIntOrNull()}}
                            if(shared!=null && compactKeyboard)Text(card?.name?:"${shared.set} ${shared.number}",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=PremiumGold)
                            if(shared!=null && !compactKeyboard){Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                                CardArtwork(card,Modifier.size(56.dp,78.dp),language=shared.language,variant=shared.variant)
                                Column {Text(card?.name?:"${shared.set} ${shared.number}",fontWeight=FontWeight.Bold);Text("${shared.set} · ${shared.language} · ${shared.variant}",fontSize=11.sp);Text("Ouvrir la carte ›",color=PremiumGold,fontSize=12.sp)}
                            }}
                        }
                    }
                }
                if(!compactKeyboard)Text(messengerTime(message.createdAt)+(if(own) when{signal.seenAt>=message.createdAt && message.createdAt>0->" · Lu";signal.deliveredAt>=message.createdAt && message.createdAt>0->" · Reçu";else->" · Envoyé"}else ""),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=5.dp,vertical=3.dp))
            }
        }
    }
    if(signal.typing && now-signal.typingAt in 0..10_000)Text("$peerName écrit…",fontSize=12.sp,color=PremiumGold)
    if(!online)Text("Hors connexion",fontSize=12.sp)
    if(error!=null)Row(verticalAlignment=Alignment.CenterVertically){Text(error,Modifier.weight(1f),fontSize=12.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.error);TextButton(onClick=onSend,enabled=online&&!busy){Text("Réessayer")}}
    if(reply!=null && !compactKeyboard)Row(verticalAlignment=Alignment.CenterVertically){Text("Réponse : ${reply.text.ifBlank{reply.deckName}.take(100)}",Modifier.weight(1f),fontSize=12.sp);IconButton(onClick=onCancelReply){Icon(Icons.Default.Close,"Annuler la réponse")}}
    Row(Modifier.fillMaxWidth().testTag("messenger_composer"),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        IconButton(onClick=onShare,enabled=!busy&&online&&error==null){Icon(Icons.Default.Add,"Partager un deck")}
        OutlinedTextField(draft,onDraft,enabled=error==null,label={Text("Ton message")},modifier=Modifier.weight(1f).testTag("messenger_draft"),singleLine=compactKeyboard,maxLines=if(compactKeyboard)1 else 4,
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={if(draft.isNotBlank()&&online&&!busy)onSend()}))
        FilledIconButton(onClick=onSend,enabled=!busy&&online&&draft.isNotBlank(),modifier=Modifier.size(48.dp).testTag("messenger_send")){
            if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Default.Send,"Envoyer")
        }
    }
    selected?.let {message->AlertDialog(onDismissRequest={selected=null},title={Text("Message")},text={Column{
        TextButton(onClick={clipboard.setText(AnnotatedString(message.text.ifBlank{message.deckName}));selected=null}){Text("Copier")}
        TextButton(onClick={onReply(message);selected=null}){Text("Répondre à ce message")}
        TextButton(onClick={hide=message;selected=null}){Text("Supprimer pour moi")}
    }},confirmButton={TextButton(onClick={selected=null}){Text("Fermer")}})}
    hide?.let{message->AlertDialog(onDismissRequest={hide=null},title={Text("Supprimer pour toi ?")},text={Text("Ce message restera visible pour ton ami.")},confirmButton={TextButton(onClick={onHide(message);hide=null}){Text("Supprimer")}},dismissButton={TextButton(onClick={hide=null}){Text("Annuler")}})}
}
internal fun messengerTime(at:Long):String=if(at<=0)"" else java.text.SimpleDateFormat("dd/MM · HH:mm",java.util.Locale.FRANCE).format(java.util.Date(at))
