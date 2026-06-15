package com.fitpro.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitpro.data.local.dao.*
import com.fitpro.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@Database(
    entities = [
        FoodItemEntity::class,
        MealEntryEntity::class,
        TrainingSessionEntity::class,
        BodyMetricEntity::class,
        LabExamEntity::class,
        ChatMessageEntity::class,
        UserGoalEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun trainingSessionDao(): TrainingSessionDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun labExamDao(): LabExamDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userGoalDao(): UserGoalDao
    abstract fun cardapioDao(): CardapioDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitpro_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    database.foodItemDao().insertFoods(SEED_FOODS)
                    database.userGoalDao().setGoal(UserGoalEntity())
                    seedTemplates(database)
                    seedShoppingList(database)
                }
            }
        }
    }
}

// ─── Seed: Food items ─────────────────────────────────────────────────────────

private val SEED_FOODS = listOf(
    FoodItemEntity(id=1,  name="Arroz branco cozido",       brand="Base", caloriesPer100g=128f, proteinPer100g=2.5f,  carbsPer100g=28.1f, fatPer100g=0.2f, fiberPer100g=1.6f, isCustom=false),
    FoodItemEntity(id=2,  name="Arroz integral cozido",     brand="Base", caloriesPer100g=124f, proteinPer100g=2.6f,  carbsPer100g=25.8f, fatPer100g=1.0f, fiberPer100g=2.1f, isCustom=false),
    FoodItemEntity(id=3,  name="Feijao carioca cozido",     brand="Base", caloriesPer100g=76f,  proteinPer100g=4.8f,  carbsPer100g=13.6f, fatPer100g=0.5f, fiberPer100g=8.5f, isCustom=false),
    FoodItemEntity(id=4,  name="Feijao preto cozido",       brand="Base", caloriesPer100g=77f,  proteinPer100g=5.0f,  carbsPer100g=14.0f, fatPer100g=0.5f, fiberPer100g=8.7f, isCustom=false),
    FoodItemEntity(id=5,  name="Peito de frango grelhado",  brand="Base", caloriesPer100g=159f, proteinPer100g=28.2f, carbsPer100g=0f,    fatPer100g=4.3f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=6,  name="Frango coxa cozida",        brand="Base", caloriesPer100g=189f, proteinPer100g=19.7f, carbsPer100g=0f,    fatPer100g=11.9f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=7,  name="Patinho bovino grelhado",   brand="Base", caloriesPer100g=219f, proteinPer100g=28.7f, carbsPer100g=0f,    fatPer100g=11.5f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=8,  name="Patinho moido refogado",    brand="Base", caloriesPer100g=230f, proteinPer100g=27.0f, carbsPer100g=2.0f,  fatPer100g=13.0f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=9,  name="Ovo cozido inteiro",        brand="Base", caloriesPer100g=155f, proteinPer100g=13.0f, carbsPer100g=1.1f,  fatPer100g=10.6f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=10, name="Clara de ovo cozida",       brand="Base", caloriesPer100g=52f,  proteinPer100g=10.9f, carbsPer100g=0.7f,  fatPer100g=0.2f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=11, name="Atum em lata agua",         brand="Base", caloriesPer100g=132f, proteinPer100g=29.0f, carbsPer100g=0f,    fatPer100g=1.5f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=12, name="Salmao grelhado",           brand="Base", caloriesPer100g=208f, proteinPer100g=20.4f, carbsPer100g=0f,    fatPer100g=13.4f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=13, name="Batata doce cozida",        brand="Base", caloriesPer100g=93f,  proteinPer100g=1.4f,  carbsPer100g=21.4f, fatPer100g=0.1f, fiberPer100g=3.3f, isCustom=false),
    FoodItemEntity(id=14, name="Batata inglesa cozida",     brand="Base", caloriesPer100g=86f,  proteinPer100g=1.9f,  carbsPer100g=19.6f, fatPer100g=0.1f, fiberPer100g=1.8f, isCustom=false),
    FoodItemEntity(id=15, name="Mandioca cozida",           brand="Base", caloriesPer100g=155f, proteinPer100g=1.2f,  carbsPer100g=36.4f, fatPer100g=0.3f, fiberPer100g=1.9f, isCustom=false),
    FoodItemEntity(id=16, name="Inhame cozido",             brand="Base", caloriesPer100g=118f, proteinPer100g=1.5f,  carbsPer100g=27.5f, fatPer100g=0.1f, fiberPer100g=4.1f, isCustom=false),
    FoodItemEntity(id=17, name="Aveia em flocos",           brand="Base", caloriesPer100g=394f, proteinPer100g=13.9f, carbsPer100g=67.0f, fatPer100g=8.5f, fiberPer100g=10.6f,isCustom=false),
    FoodItemEntity(id=18, name="Macarrao cozido",           brand="Base", caloriesPer100g=158f, proteinPer100g=5.5f,  carbsPer100g=31.2f, fatPer100g=1.0f, fiberPer100g=1.8f, isCustom=false),
    FoodItemEntity(id=19, name="Pao integral",              brand="Base", caloriesPer100g=253f, proteinPer100g=9.2f,  carbsPer100g=47.0f, fatPer100g=3.5f, fiberPer100g=6.9f, isCustom=false),
    FoodItemEntity(id=20, name="Banana nanica",             brand="Base", caloriesPer100g=92f,  proteinPer100g=1.1f,  carbsPer100g=23.8f, fatPer100g=0.1f, fiberPer100g=1.9f, isCustom=false),
    FoodItemEntity(id=21, name="Maca",                      brand="Base", caloriesPer100g=56f,  proteinPer100g=0.3f,  carbsPer100g=15.2f, fatPer100g=0.1f, fiberPer100g=2.4f, isCustom=false),
    FoodItemEntity(id=22, name="Mamao",                     brand="Base", caloriesPer100g=45f,  proteinPer100g=0.5f,  carbsPer100g=11.8f, fatPer100g=0.1f, fiberPer100g=1.8f, isCustom=false),
    FoodItemEntity(id=23, name="Abacate",                   brand="Base", caloriesPer100g=160f, proteinPer100g=2.1f,  carbsPer100g=8.8f,  fatPer100g=14.7f,fiberPer100g=6.8f, isCustom=false),
    FoodItemEntity(id=24, name="Leite desnatado",           brand="Base", caloriesPer100g=37f,  proteinPer100g=3.5f,  carbsPer100g=5.1f,  fatPer100g=0.1f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=25, name="Iogurte natural",           brand="Base", caloriesPer100g=49f,  proteinPer100g=5.2f,  carbsPer100g=6.9f,  fatPer100g=0.2f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=26, name="Queijo cottage",            brand="Base", caloriesPer100g=98f,  proteinPer100g=11.1f, carbsPer100g=3.4f,  fatPer100g=4.3f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=27, name="Queijo mussarela",          brand="Base", caloriesPer100g=300f, proteinPer100g=22.0f, carbsPer100g=0.5f,  fatPer100g=24.0f,fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=28, name="Whey Protein Dux Conc.",    brand="Dux",  caloriesPer100g=407f, proteinPer100g=66.7f, carbsPer100g=18.3f, fatPer100g=7.3f, fiberPer100g=0f,   servingSizeG=30f, isCustom=false),
    FoodItemEntity(id=29, name="Azeite extravirgem",        brand="Base", caloriesPer100g=884f, proteinPer100g=0f,    carbsPer100g=0f,    fatPer100g=100f, fiberPer100g=0f,   isCustom=false),
    FoodItemEntity(id=30, name="Amendoim torrado s sal",    brand="Base", caloriesPer100g=581f, proteinPer100g=26.2f, carbsPer100g=16.1f, fatPer100g=47.5f,fiberPer100g=8.0f, isCustom=false),
    FoodItemEntity(id=31, name="Alface",                    brand="Base", caloriesPer100g=15f,  proteinPer100g=1.4f,  carbsPer100g=2.9f,  fatPer100g=0.2f, fiberPer100g=1.3f, isCustom=false),
    FoodItemEntity(id=32, name="Tomate cru",                brand="Base", caloriesPer100g=19f,  proteinPer100g=1.0f,  carbsPer100g=3.9f,  fatPer100g=0.2f, fiberPer100g=1.2f, isCustom=false),
    FoodItemEntity(id=33, name="Brocolis cozido",           brand="Base", caloriesPer100g=35f,  proteinPer100g=2.3f,  carbsPer100g=6.6f,  fatPer100g=0.4f, fiberPer100g=3.3f, isCustom=false),
    FoodItemEntity(id=34, name="Cenoura cozida",            brand="Base", caloriesPer100g=35f,  proteinPer100g=0.8f,  carbsPer100g=8.2f,  fatPer100g=0.2f, fiberPer100g=3.0f, isCustom=false),
    FoodItemEntity(id=35, name="Flocao de milho Marata",    brand="Marata", caloriesPer100g=370f,proteinPer100g=7.5f,  carbsPer100g=80.0f, fatPer100g=1.5f, fiberPer100g=1.0f, isCustom=false),
)

// ─── Seed: Templates ──────────────────────────────────────────────────────────

private suspend fun seedTemplates(db: AppDatabase) {
    if (db.cardapioDao().count() > 0) return

    // Breakfast template
    val cafeId = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Cafe da manha padrao", mealType=MealType.BREAKFAST,
            description="Shake (leite+whey+aveia+banana) + 3 ovos com mussarela", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=cafeId, foodItemId=24, quantityG=200f), // leite desnatado
        MealTemplateItemEntity(templateId=cafeId, foodItemId=28, quantityG=30f),  // whey dux
        MealTemplateItemEntity(templateId=cafeId, foodItemId=17, quantityG=30f),  // aveia
        MealTemplateItemEntity(templateId=cafeId, foodItemId=20, quantityG=100f), // banana
        MealTemplateItemEntity(templateId=cafeId, foodItemId=9,  quantityG=165f), // 3 ovos ~55g each
        MealTemplateItemEntity(templateId=cafeId, foodItemId=27, quantityG=15f),  // mussarela
    ))

    // Lunch templates
    val almFrangoArroz = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Almoco - Frango com Arroz", mealType=MealType.LUNCH,
            description="Frango 200g + arroz 150g + salada + azeite", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=almFrangoArroz, foodItemId=5,  quantityG=200f), // frango peito
        MealTemplateItemEntity(templateId=almFrangoArroz, foodItemId=1,  quantityG=150f), // arroz
        MealTemplateItemEntity(templateId=almFrangoArroz, foodItemId=31, quantityG=60f),  // alface
        MealTemplateItemEntity(templateId=almFrangoArroz, foodItemId=32, quantityG=80f),  // tomate
        MealTemplateItemEntity(templateId=almFrangoArroz, foodItemId=29, quantityG=10f),  // azeite
    ))

    val almFrangoMac = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Almoco - Frango com Macarrao", mealType=MealType.LUNCH,
            description="Frango 200g + macarrao 150g + salada + azeite", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=almFrangoMac, foodItemId=5,  quantityG=200f),
        MealTemplateItemEntity(templateId=almFrangoMac, foodItemId=18, quantityG=150f), // macarrão
        MealTemplateItemEntity(templateId=almFrangoMac, foodItemId=31, quantityG=60f),
        MealTemplateItemEntity(templateId=almFrangoMac, foodItemId=29, quantityG=10f),
    ))

    val almPatinho = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Almoco - Patinho com Arroz", mealType=MealType.LUNCH,
            description="Patinho moido 180g + arroz 150g + salada", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=almPatinho, foodItemId=8,  quantityG=180f), // patinho moido
        MealTemplateItemEntity(templateId=almPatinho, foodItemId=1,  quantityG=150f),
        MealTemplateItemEntity(templateId=almPatinho, foodItemId=31, quantityG=60f),
        MealTemplateItemEntity(templateId=almPatinho, foodItemId=32, quantityG=80f),
    ))

    // Snack template
    val lancheId = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Lanche padrao", mealType=MealType.SNACK,
            description="2 ovos + 30g amendoim + 1 banana", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=lancheId, foodItemId=9,  quantityG=110f), // 2 ovos
        MealTemplateItemEntity(templateId=lancheId, foodItemId=30, quantityG=30f),  // amendoim
        MealTemplateItemEntity(templateId=lancheId, foodItemId=20, quantityG=100f), // banana
    ))

    // Dinner templates
    val janFrangoArroz = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Jantar - Frango Coxa com Arroz", mealType=MealType.DINNER,
            description="Frango coxa 160g + arroz 120g + brocolis", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=janFrangoArroz, foodItemId=6,  quantityG=160f), // coxa
        MealTemplateItemEntity(templateId=janFrangoArroz, foodItemId=1,  quantityG=120f),
        MealTemplateItemEntity(templateId=janFrangoArroz, foodItemId=33, quantityG=100f), // brocolis
        MealTemplateItemEntity(templateId=janFrangoArroz, foodItemId=29, quantityG=8f),
    ))

    val janFrangoMac = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Jantar - Frango Coxa com Macarrao", mealType=MealType.DINNER,
            description="Frango coxa 160g + macarrao 130g + cenoura", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=janFrangoMac, foodItemId=6,  quantityG=160f),
        MealTemplateItemEntity(templateId=janFrangoMac, foodItemId=18, quantityG=130f),
        MealTemplateItemEntity(templateId=janFrangoMac, foodItemId=34, quantityG=80f),
    ))

    val janOmelete = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Jantar - Omelete com Arroz", mealType=MealType.DINNER,
            description="4 ovos mexidos + arroz 150g + salada", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=janOmelete, foodItemId=9,  quantityG=220f), // 4 ovos
        MealTemplateItemEntity(templateId=janOmelete, foodItemId=1,  quantityG=150f),
        MealTemplateItemEntity(templateId=janOmelete, foodItemId=31, quantityG=60f),
        MealTemplateItemEntity(templateId=janOmelete, foodItemId=32, quantityG=80f),
    ))

    val janMandioca = db.cardapioDao().insertTemplate(
        MealTemplateEntity(name="Jantar - Frango Coxa com Mandioca", mealType=MealType.DINNER,
            description="Frango coxa 160g + mandioca 180g + brocolis", isDefault=true)
    )
    db.cardapioDao().insertTemplateItems(listOf(
        MealTemplateItemEntity(templateId=janMandioca, foodItemId=6,  quantityG=160f),
        MealTemplateItemEntity(templateId=janMandioca, foodItemId=15, quantityG=180f),
        MealTemplateItemEntity(templateId=janMandioca, foodItemId=33, quantityG=80f),
    ))
}

// ─── Seed: Shopping list ──────────────────────────────────────────────────────

private suspend fun seedShoppingList(db: AppDatabase) {
    val now = LocalDate.now()
    if (db.shoppingDao().countForMonth(now.monthValue, now.year) > 0) return

    val m = now.monthValue; val y = now.year
    val items = listOf(
        // PROTEÍNAS
        ShoppingItemEntity(name="Ovos (cx 30 unid.)",            category=ShoppingCategory.PROTEINAS,      quantity="5 caixas",  estimatedPrice=110f, month=m, year=y),
        ShoppingItemEntity(name="Peito de frango",               category=ShoppingCategory.PROTEINAS,      quantity="5 kg",      estimatedPrice=125f, month=m, year=y),
        ShoppingItemEntity(name="Frango coxa/sobrecoxa",         category=ShoppingCategory.PROTEINAS,      quantity="4 kg",      estimatedPrice=68f,  month=m, year=y),
        ShoppingItemEntity(name="Patinho moido",                 category=ShoppingCategory.PROTEINAS,      quantity="700 g",     estimatedPrice=35f,  month=m, year=y),
        ShoppingItemEntity(name="Queijo cottage (400g)",         category=ShoppingCategory.PROTEINAS,      quantity="3 potes",   estimatedPrice=33f,  month=m, year=y),
        ShoppingItemEntity(name="Iogurte natural (500g)",        category=ShoppingCategory.PROTEINAS,      quantity="4 potes",   estimatedPrice=28f,  month=m, year=y),
        ShoppingItemEntity(name="Queijo mussarela",              category=ShoppingCategory.PROTEINAS,      quantity="700 g",     estimatedPrice=28f,  month=m, year=y, notes="R\$39,98/kg — comprar bloco inteiro"),
        ShoppingItemEntity(name="Leite desnatado",               category=ShoppingCategory.PROTEINAS,      quantity="10,5 L",    estimatedPrice=37f,  month=m, year=y),
        // CARBOIDRATOS
        ShoppingItemEntity(name="Arroz branco",                  category=ShoppingCategory.CARBOIDRATOS,   quantity="5 kg",      estimatedPrice=25f,  month=m, year=y),
        ShoppingItemEntity(name="Aveia em flocos (750g)",        category=ShoppingCategory.CARBOIDRATOS,   quantity="2 pacotes", estimatedPrice=16f,  month=m, year=y),
        ShoppingItemEntity(name="Macarrao (500g)",               category=ShoppingCategory.CARBOIDRATOS,   quantity="3 pacotes", estimatedPrice=12f,  month=m, year=y),
        ShoppingItemEntity(name="Pao integral",                  category=ShoppingCategory.CARBOIDRATOS,   quantity="2 pacotes", estimatedPrice=18f,  month=m, year=y),
        ShoppingItemEntity(name="Mandioca/macaxeira",            category=ShoppingCategory.CARBOIDRATOS,   quantity="2 kg",      estimatedPrice=8f,   month=m, year=y),
        ShoppingItemEntity(name="Inhame",                        category=ShoppingCategory.CARBOIDRATOS,   quantity="1 kg",      estimatedPrice=7f,   month=m, year=y),
        ShoppingItemEntity(name="Banana",                        category=ShoppingCategory.CARBOIDRATOS,   quantity="5 kg (~35 unid.)", estimatedPrice=22f, month=m, year=y),
        ShoppingItemEntity(name="Maca",                          category=ShoppingCategory.CARBOIDRATOS,   quantity="2 kg",      estimatedPrice=14f,  month=m, year=y),
        ShoppingItemEntity(name="Mamao (1 unid.)",               category=ShoppingCategory.CARBOIDRATOS,   quantity="1",         estimatedPrice=6f,   month=m, year=y),
        // GORDURAS
        ShoppingItemEntity(name="Azeite extravirgem (500ml)",    category=ShoppingCategory.GORDURAS,       quantity="1 frasco",  estimatedPrice=18f,  month=m, year=y),
        ShoppingItemEntity(name="Amendoim torrado s/ sal (500g)",category=ShoppingCategory.GORDURAS,       quantity="1 pacote",  estimatedPrice=8f,   month=m, year=y),
        // LEGUMES E TEMPEROS
        ShoppingItemEntity(name="Tomate",                        category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="3 kg",    estimatedPrice=15f,  month=m, year=y),
        ShoppingItemEntity(name="Alface",                        category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="6 pes",   estimatedPrice=15f,  month=m, year=y),
        ShoppingItemEntity(name="Brocolis",                      category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="2 cabecas", estimatedPrice=8f, month=m, year=y),
        ShoppingItemEntity(name="Cenoura",                       category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="1 kg",    estimatedPrice=5f,   month=m, year=y),
        ShoppingItemEntity(name="Cebola",                        category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="2 kg",    estimatedPrice=10f,  month=m, year=y),
        ShoppingItemEntity(name="Alho",                          category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="2 cabecas", estimatedPrice=6f, month=m, year=y),
        ShoppingItemEntity(name="Limao",                         category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="1 kg",    estimatedPrice=6f,   month=m, year=y),
        ShoppingItemEntity(name="Temperos (sal, pimenta, curcuma, colorau, oregano)", category=ShoppingCategory.LEGUMES_TEMPEROS, quantity="kit", estimatedPrice=12f, month=m, year=y),
        // GYOZA (especiais)
        ShoppingItemEntity(name="Repolho (1/4 cabeca)",          category=ShoppingCategory.OUTROS, quantity="1",           estimatedPrice=3f,   month=m, year=y, notes="Para gyoza — compra quinzenal"),
        ShoppingItemEntity(name="Sake de cozinha (ou vinho bco)",category=ShoppingCategory.OUTROS, quantity="1 garrafa",   estimatedPrice=9f,   month=m, year=y, notes="Para gyoza"),
        ShoppingItemEntity(name="Oleo de gergelim torrado 100ml",category=ShoppingCategory.OUTROS, quantity="1 frasco",   estimatedPrice=14f,  month=m, year=y, notes="Para gyoza — mercearia oriental"),
        ShoppingItemEntity(name="Shoyu light",                   category=ShoppingCategory.OUTROS, quantity="1 garrafa",   estimatedPrice=7f,   month=m, year=y),
    )
    db.shoppingDao().insertItems(items)
}
