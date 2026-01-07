"""
Speaking Scenario Sync Service
Populates speaking_scenarios table based on lesson_topics content.
Ensures speaking practice is aligned with lesson curriculum.
"""
import asyncio
from typing import List, Dict, Any, Optional
from supabase_client import SupabaseManager


class ScenarioSyncService:
    """Service to sync speaking scenarios with lesson content."""

    def __init__(self):
        self.supabase_manager = SupabaseManager()

        # Map lesson topics to speaking scenarios
        self.scenario_mapping = {
            "travel": ["travel", "directions", "hotel", "transportation"],
            "food": ["food", "restaurant", "cooking", "dining"],
            "daily_conversation": ["greetings", "introduction", "daily", "routine", "conversation"],
            "work": ["work", "business", "professional", "office", "meeting"],
            "culture": ["culture", "customs", "tradition", "festival", "holiday"]
        }

    async def sync_scenarios_from_lessons(self, language: str) -> Dict[str, Any]:
        """
        Sync speaking scenarios for a given language based on lesson topics.

        Args:
            language: Language code (English, Spanish, etc.)

        Returns:
            Dictionary with sync results
        """
        try:
            print(f"[ScenarioSync] Syncing scenarios for {language}")

            # Get all lesson topics for the language
            response = self.supabase_manager.client.table("lesson_topics").select("*").eq(
                "language", language
            ).eq("is_published", True).execute()

            lesson_topics = response.data

            if not lesson_topics:
                print(f"[ScenarioSync] No lesson topics found for {language}")
                return {
                    "success": False,
                    "message": f"No lesson topics found for {language}",
                    "scenarios_created": 0
                }

            print(f"[ScenarioSync] Found {len(lesson_topics)} lesson topics")

            # Create speaking scenarios based on lesson topics
            scenarios_created = 0

            for topic in lesson_topics:
                scenario_type = self._determine_scenario_type(topic)

                if scenario_type:
                    created = await self._create_scenario_from_topic(topic, scenario_type, language)
                    if created:
                        scenarios_created += 1

            return {
                "success": True,
                "message": f"Successfully synced {scenarios_created} scenarios for {language}",
                "scenarios_created": scenarios_created,
                "lesson_topics_processed": len(lesson_topics)
            }

        except Exception as e:
            print(f"[ScenarioSync] Error syncing scenarios: {str(e)}")
            return {
                "success": False,
                "message": f"Failed to sync scenarios: {str(e)}",
                "scenarios_created": 0
            }

    def _determine_scenario_type(self, topic: Dict[str, Any]) -> Optional[str]:
        """
        Determine which speaking scenario type matches a lesson topic.

        Args:
            topic: Lesson topic data

        Returns:
            Scenario type or None if no match
        """
        title = topic.get("title", "").lower()
        description = topic.get("description", "").lower()

        combined_text = f"{title} {description}"

        # Check each scenario type for keyword matches
        for scenario_type, keywords in self.scenario_mapping.items():
            for keyword in keywords:
                if keyword in combined_text:
                    return scenario_type

        # Default based on lesson number (if no clear match)
        lesson_num = topic.get("lesson_number")
        if lesson_num:
            if lesson_num <= 5:
                return "daily_conversation"
            elif lesson_num <= 10:
                return "travel"
            elif lesson_num <= 15:
                return "food"
            else:
                return "work"

        return None

    async def _create_scenario_from_topic(
        self,
        topic: Dict[str, Any],
        scenario_type: str,
        language: str
    ) -> bool:
        """
        Create a speaking scenario from a lesson topic.

        Args:
            topic: Lesson topic data
            scenario_type: Type of scenario
            language: Language code

        Returns:
            True if created successfully
        """
        try:
            # Check if a scenario already exists
            existing = self.supabase_manager.client.table("speaking_scenarios").select("id").eq(
                "lesson_topic_id", topic["id"]
            ).execute()

            if existing.data:
                print(f"[ScenarioSync] Scenario already exists for topic {topic['id']}")
                return False

            # Generate practice prompts based on the difficulty level
            prompts = self._generate_prompts(
                scenario_type,
                topic.get("difficulty_level", "Beginner"),
                language
            )

            # Extract expected vocabulary from lesson content
            expected_vocabulary = self._extract_vocabulary(topic)

            # Create a scenario
            scenario_data = {
                "lesson_topic_id": topic["id"],
                "language": language,
                "difficulty_level": topic.get("difficulty_level", "Beginner").lower(),
                "scenario_type": scenario_type,
                "title": f"{topic.get('title', 'Practice')} - Speaking Practice",
                "description": f"Practice speaking based on: {topic.get('description', '')}",
                "prompts": prompts,
                "expected_vocabulary": expected_vocabulary,
                "sort_order": topic.get("sort_order", 0),
                "is_active": True
            }

            self.supabase_manager.client.table("speaking_scenarios").insert(scenario_data).execute()

            print(f"[ScenarioSync] Created scenario for topic: {topic['title']}")
            return True

        except Exception as e:
            print(f"[ScenarioSync] Error creating scenario: {str(e)}")
            return False

    def _generate_prompts(
        self,
        scenario_type: str,
        difficulty_level: str,
        language: str
    ) -> List[str]:
        """
        Generate practice prompts for a scenario.

        Args:
            scenario_type: Type of scenario
            difficulty_level: Beginner, Intermediate, or Advanced
            language: Target language

        Returns:
            List of practice prompts
        """
        # Template prompts by scenario and difficulty
        prompt_templates = {
            "travel": {
                "beginner": [
                    "How do I get to the train station?",
                    "Where is the nearest hotel?",
                    "Can you help me find a taxi?",
                    "How much is a ticket?",
                    "What time does it leave?"
                ],
                "intermediate": [
                    "Can you recommend a good restaurant nearby?",
                    "I need to book a room for two nights.",
                    "What are the must-see attractions?",
                    "Is there a pharmacy around here?",
                    "How do I get to the city center?"
                ],
                "advanced": [
                    "What are some off-the-beaten-path places to visit?",
                    "Can you explain the local customs I should know about?",
                    "What's the best way to experience authentic local culture?",
                    "I'm interested in historical sites, what do you suggest?",
                    "How can I make the most of my limited time here?"
                ]
            },
            "food": {
                "beginner": [
                    "I would like water, please.",
                    "What do you recommend?",
                    "Can I see the menu?",
                    "This looks delicious!",
                    "The check, please."
                ],
                "intermediate": [
                    "What's today's special?",
                    "Is this dish spicy?",
                    "I'm allergic to nuts.",
                    "Can you make it vegetarian?",
                    "What wine pairs well with this?"
                ],
                "advanced": [
                    "Can you tell me about the regional cuisine?",
                    "What cooking techniques are used in this dish?",
                    "How has this dish evolved over time?",
                    "What are the key ingredients in traditional cooking?",
                    "Can you explain the cultural significance of this meal?"
                ]
            },
            "daily_conversation": {
                "beginner": [
                    "Hello! How are you?",
                    "My name is...",
                    "Nice to meet you!",
                    "Thank you very much.",
                    "See you later!"
                ],
                "intermediate": [
                    "What did you do today?",
                    "How was your weekend?",
                    "What are your hobbies?",
                    "Do you like to travel?",
                    "What's your favorite season?"
                ],
                "advanced": [
                    "What's your opinion on current events?",
                    "How do you balance work and personal life?",
                    "What motivates you in your career?",
                    "How has technology changed your daily life?",
                    "What are your thoughts on environmental issues?"
                ]
            },
            "work": {
                "beginner": [
                    "I have a meeting at 2pm.",
                    "Can you send me the file?",
                    "When is the deadline?",
                    "I need help with this.",
                    "Thank you for your time."
                ],
                "intermediate": [
                    "Let's schedule a meeting to discuss the project.",
                    "Can you review the quarterly report?",
                    "We need to adjust the timeline.",
                    "I'd like to propose a new approach.",
                    "What are the next steps?"
                ],
                "advanced": [
                    "Let's analyze the market trends and strategic implications.",
                    "How can we optimize our workflow for better efficiency?",
                    "What are the key performance indicators we should focus on?",
                    "I'd like to discuss the long-term strategic direction.",
                    "How can we improve cross-team collaboration?"
                ]
            },
            "culture": {
                "beginner": [
                    "This is beautiful!",
                    "Tell me about this tradition.",
                    "When is the festival?",
                    "Happy holidays!",
                    "I love learning about your culture."
                ],
                "intermediate": [
                    "Can you explain this historical event?",
                    "What are some cultural etiquette tips?",
                    "How do people celebrate this festival?",
                    "What's the significance of this symbol?",
                    "Tell me about traditional crafts."
                ],
                "advanced": [
                    "How has globalization affected local traditions?",
                    "What role does religion play in daily life?",
                    "Can you discuss the influence of historical events on modern culture?",
                    "How do modern and traditional values coexist?",
                    "What are the current cultural trends among youth?"
                ]
            }
        }

        difficulty_key = difficulty_level.lower()
        return prompt_templates.get(scenario_type, {}).get(difficulty_key, [
            "Please practice speaking clearly.",
            "Describe your day.",
            "Tell me about yourself."
        ])

    def _extract_vocabulary(self, topic: Dict[str, Any]) -> List[str]:
        """
        Extract key vocabulary from a lesson topic.

        Args:
            topic: Lesson topic data

        Returns:
            List of key vocabulary words
        """
        # This is a simple extraction - in practice, you'd query related vocabulary
        title = topic.get("title", "")
        description = topic.get("description", "")

        # Extract important words (simple implementation)
        words = []
        for text in [title, description]:
            for word in text.split():
                if len(word) > 4 and word.isalpha():
                    words.append(word.capitalize())

        return words[:10]  # Return top 10

    async def sync_all_languages(self) -> Dict[str, Any]:
        """
        Sync scenarios for all available languages.

        Returns:
            Dictionary with overall results
        """
        languages = ["english", "spanish", "french", "german", "korean", "mandarin"]

        results = {}
        total_created = 0

        for language in languages:
            result = await self.sync_scenarios_from_lessons(language)
            results[language] = result
            total_created += result.get("scenarios_created", 0)

        return {
            "success": True,
            "total_scenarios_created": total_created,
            "by_language": results
        }


# Example usage
async def main():
    """Example: Sync scenarios for all languages."""
    service = ScenarioSyncService()
    result = await service.sync_all_languages()
    print(f"Sync complete: {result}")


if __name__ == "__main__":
    asyncio.run(main())
