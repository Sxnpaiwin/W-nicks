package gg.lode.nametag.util;

import java.util.List;
import java.util.Random;

public class UsernameGenerator {
   private static final Random RANDOM = new Random();
   private static final List<String> COMMON_WORDS = List.of(
      "Shadow",
      "Fire",
      "Ice",
      "Storm",
      "Thunder",
      "Light",
      "Dark",
      "Night",
      "Sky",
      "Ocean",
      "Forest",
      "Mountain",
      "River",
      "Desert",
      "Snow",
      "Rain",
      "Wind",
      "Earth",
      "Water",
      "Frost",
      "Dragon",
      "Phoenix",
      "Wolf",
      "Tiger",
      "Lion",
      "Eagle",
      "Falcon",
      "Raven",
      "Bear",
      "Fox",
      "Knight",
      "Warrior",
      "Mage",
      "Archer",
      "Hunter",
      "Guardian",
      "Protector",
      "Defender",
      "Ranger",
      "Scout",
      "Legend",
      "Hero",
      "Master",
      "King",
      "Queen",
      "Prince",
      "Princess",
      "Lord",
      "Lady",
      "Sir",
      "Mystic",
      "Magic",
      "Spell",
      "Crystal",
      "Diamond",
      "Gold",
      "Silver",
      "Iron",
      "Steel",
      "Stone",
      "Blade",
      "Sword",
      "Bow",
      "Arrow",
      "Shield",
      "Armor",
      "Helmet",
      "Crown",
      "Ring",
      "Gem",
      "Star",
      "Moon",
      "Sun",
      "Galaxy",
      "Cosmos",
      "Nebula",
      "Comet",
      "Asteroid",
      "Planet",
      "Universe",
      "Demon",
      "Angel",
      "Ghost",
      "Spirit",
      "Soul",
      "Heart",
      "Mind",
      "Dream",
      "Fantasy",
      "Reality",
      "Truth",
      "Justice",
      "Honor",
      "Courage",
      "Strength",
      "Power",
      "Energy",
      "Flame",
      "Blaze",
      "Lightning"
   );
   private static final List<String> YEARS = List.of(
      "1990",
      "1991",
      "1992",
      "1993",
      "1994",
      "1995",
      "1996",
      "1997",
      "1998",
      "1999",
      "2000",
      "2001",
      "2002",
      "2003",
      "2004",
      "2005",
      "2006",
      "2007",
      "2008",
      "2009",
      "2010",
      "2011",
      "2012",
      "2013",
      "2014",
      "2015",
      "2016",
      "2017",
      "2018",
      "2019",
      "2020",
      "2021",
      "2022",
      "2023",
      "2024"
   );
   private static final List<String> PREFIXES = List.of("The", "Pro", "Ultra", "Mega", "Super", "Hyper", "Epic", "Legendary", "Elite", "Prime", "Alpha", "Beta");
   private static final List<String> SUFFIXES = List.of("YT", "MC", "Gamer", "Player", "Master", "Pro", "HD", "Gaming", "Craft", "Build", "Mine", "Survival");
   private static final List<String> NUMBERS = List.of(
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9",
      "10",
      "11",
      "12",
      "13",
      "14",
      "15",
      "16",
      "17",
      "18",
      "19",
      "20",
      "21",
      "22",
      "23",
      "24",
      "25",
      "26",
      "27",
      "28",
      "29",
      "30",
      "99",
      "100",
      "200",
      "300",
      "500",
      "1000",
      "2000",
      "3000"
   );

   public static String generateUsername() {
      int pattern = RANDOM.nextInt(7);

      return switch (pattern) {
         case 0 -> generateYearPattern();
         case 1 -> generateNumberPattern();
         case 2 -> generatePrefixPattern();
         case 3 -> generateSuffixPattern();
         case 4 -> generateDoubleWordPattern();
         case 5 -> generateUnderscorePattern();
         default -> generateSimplePattern();
      };
   }

   private static String generateYearPattern() {
      String word = getRandomWord();
      String year = getRandomYear();
      return RANDOM.nextBoolean() ? word + year : year + word;
   }

   private static String generateNumberPattern() {
      String word = getRandomWord();
      String number = getRandomNumber();
      return RANDOM.nextBoolean() ? word + number : number + word;
   }

   private static String generatePrefixPattern() {
      String prefix = getRandomPrefix();
      String word = getRandomWord();
      return RANDOM.nextBoolean() ? prefix + word + getRandomNumber() : prefix + word;
   }

   private static String generateSuffixPattern() {
      String word = getRandomWord();
      String suffix = getRandomSuffix();
      return RANDOM.nextBoolean() ? word + suffix + getRandomNumber() : word + suffix;
   }

   private static String generateDoubleWordPattern() {
      String word1 = getRandomWord();
      String word2 = getRandomWord();

      while (word1.equals(word2)) {
         word2 = getRandomWord();
      }

      return RANDOM.nextBoolean() ? word1 + word2 : word1 + "_" + word2;
   }

   private static String generateUnderscorePattern() {
      String word1 = getRandomWord();
      String word2 = getRandomWord();

      while (word1.equals(word2)) {
         word2 = getRandomWord();
      }

      return RANDOM.nextBoolean() ? word1 + "_" + word2 + "_" + getRandomNumber() : word1 + "_" + word2;
   }

   private static String generateSimplePattern() {
      String word = getRandomWord();
      return RANDOM.nextBoolean() ? word + getRandomNumber() : word;
   }

   private static String getRandomWord() {
      return COMMON_WORDS.get(RANDOM.nextInt(COMMON_WORDS.size()));
   }

   private static String getRandomYear() {
      return YEARS.get(RANDOM.nextInt(YEARS.size()));
   }

   private static String getRandomPrefix() {
      return PREFIXES.get(RANDOM.nextInt(PREFIXES.size()));
   }

   private static String getRandomSuffix() {
      return SUFFIXES.get(RANDOM.nextInt(SUFFIXES.size()));
   }

   private static String getRandomNumber() {
      return NUMBERS.get(RANDOM.nextInt(NUMBERS.size()));
   }
}
