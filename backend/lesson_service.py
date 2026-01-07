"""
Service layer for lesson content operations.
Handles all database interactions for lessons, questions, and user progress.
"""
import logging
from typing import List, Optional, Dict, Any
from datetime import datetime
from supabase import Client

from lesson_models import (
    Lesson, LessonCreate, LessonUpdate, LessonSummary,
    Question, QuestionCreate, QuestionUpdate,
    QuestionChoice, QuestionChoiceCreate,
    UserLessonProgress, UserLessonProgressCreate, UserLessonProgressUpdate,
    UserQuestionAnswer, UserQuestionAnswerCreate,
    QuestionType
)
from supabase_client import get_supabase

logger = logging.getLogger(__name__)


class LessonService:
    """Service for managing lessons and their content"""
    
    def __init__(self):
        self.supabase: Optional[Client] = get_supabase()
        if not self.supabase:
            logger.warning("Supabase client not available")

    # LESSON CRUD OPERATIONS

    async def get_lessons_by_topic(
        self, 
        topic_id: str, 
        published_only: bool = True
    ) -> List[LessonSummary]:
        """Get all lessons for a specific topic"""
        try:
            query = self.supabase.table("lessons").select(
                "*, questions(id)"
            ).eq("topic_id", topic_id).order("lesson_order")
            
            if published_only:
                query = query.eq("is_published", True)
            
            response = query.execute()
            
            lessons = []
            for row in response.data:
                lesson = LessonSummary(
                    id=row["id"],
                    topic_id=row["topic_id"],
                    title=row["title"],
                    description=row.get("description"),
                    lesson_order=row["lesson_order"],
                    is_published=row["is_published"],
                    question_count=len(row.get("questions", [])),
                    created_at=row["created_at"],
                    updated_at=row["updated_at"]
                )
                lessons.append(lesson)
            
            return lessons
        except Exception as e:
            logger.error(f"Error fetching lessons for topic {topic_id}: {e}")
            raise

    async def get_lesson_by_id(
        self, 
        lesson_id: str, 
        include_questions: bool = True
    ) -> Optional[Lesson]:
        """Get a specific lesson with all its questions and choices"""
        try:
            if include_questions:
                # Fetch lesson with nested questions and choices
                response = self.supabase.table("lessons").select(
                    """
                    *,
                    questions (
                        *,
                        question_choices (*)
                    )
                    """
                ).eq("id", lesson_id).execute()
            else:
                response = self.supabase.table("lessons").select("*").eq(
                    "id", lesson_id
                ).execute()
            
            if not response.data:
                return None
            
            lesson_data = response.data[0]
            
            # Parse questions if included
            questions = []
            if include_questions and "questions" in lesson_data:
                for q in lesson_data["questions"]:
                    choices = [
                        QuestionChoice(
                            id=c["id"],
                            question_id=c["question_id"],
                            choice_text=c["choice_text"],
                            choice_order=c["choice_order"],
                            is_correct=c["is_correct"],
                            image_url=c.get("image_url"),
                            audio_url=c.get("audio_url"),
                            match_pair_id=c.get("match_pair_id"),  # ✅ Added for matching questions
                            created_at=c["created_at"]
                        )
                        for c in sorted(q.get("question_choices", []), key=lambda x: x["choice_order"])
                    ]
                    
                    question = Question(
                        id=q["id"],
                        lesson_id=q["lesson_id"],
                        question_type=q["question_type"],
                        question_text=q["question_text"],
                        question_order=q["question_order"],
                        answer_text=q.get("answer_text"),
                        question_audio_url=q.get("question_audio_url"),
                        answer_audio_url=q.get("answer_audio_url"),
                        error_text=q.get("error_text"),  
                        explanation=q.get("explanation"),  
                        wrong_answer_feedback=q.get("wrong_answer_feedback"),  
                        choices=choices,
                        created_at=q["created_at"],
                        updated_at=q["updated_at"]
                    )
                    questions.append(question)
                
                questions.sort(key=lambda x: x.question_order)
            
            lesson = Lesson(
                id=lesson_data["id"],
                topic_id=lesson_data["topic_id"],
                title=lesson_data["title"],
                description=lesson_data.get("description"),
                lesson_order=lesson_data["lesson_order"],
                is_published=lesson_data["is_published"],
                questions=questions,
                created_at=lesson_data["created_at"],
                updated_at=lesson_data["updated_at"]
            )
            
            return lesson
        except Exception as e:
            logger.error(f"Error fetching lesson {lesson_id}: {e}")
            raise

    async def create_lesson(self, lesson_data: LessonCreate) -> Lesson:
        """Create a new lesson with questions"""
        try:
            # Create a lesson
            lesson_dict = {
                "topic_id": lesson_data.topic_id,
                "title": lesson_data.title,
                "description": lesson_data.description,
                "lesson_order": lesson_data.lesson_order,
                "is_published": lesson_data.is_published
            }
            
            response = self.supabase.table("lessons").insert(lesson_dict).execute()
            lesson_id = response.data[0]["id"]
            
            # Create questions if provided
            if lesson_data.questions:
                for question_data in lesson_data.questions:
                    await self.create_question(lesson_id, question_data)
            
            # Return complete lesson
            return await self.get_lesson_by_id(lesson_id)
        except Exception as e:
            logger.error(f"Error creating lesson: {e}")
            raise

    async def update_lesson(
        self, 
        lesson_id: str, 
        lesson_data: LessonUpdate
    ) -> Lesson:
        """Update an existing lesson"""
        try:
            update_dict = lesson_data.model_dump(exclude_none=True)
            
            if update_dict:
                self.supabase.table("lessons").update(update_dict).eq(
                    "id", lesson_id
                ).execute()
            
            return await self.get_lesson_by_id(lesson_id)
        except Exception as e:
            logger.error(f"Error updating lesson {lesson_id}: {e}")
            raise

    async def delete_lesson(self, lesson_id: str) -> bool:
        """Delete a lesson (cascade deletes questions and choices)"""
        try:
            self.supabase.table("lessons").delete().eq("id", lesson_id).execute()
            return True
        except Exception as e:
            logger.error(f"Error deleting lesson {lesson_id}: {e}")
            raise

   
    # QUESTION CRUD OPERATIONS
   
    
    async def create_question(
        self, 
        lesson_id: str, 
        question_data: QuestionCreate
    ) -> Question:
        """Create a new question with choices"""
        try:
            # Create question
            question_dict = {
                "lesson_id": lesson_id,
                "question_type": question_data.question_type.value,
                "question_text": question_data.question_text,
                "question_order": question_data.question_order,
                "answer_text": question_data.answer_text,
                "question_audio_url": question_data.question_audio_url,
                "answer_audio_url": question_data.answer_audio_url,
                "error_text": question_data.error_text,
                "explanation": question_data.explanation,
                "wrong_answer_feedback": question_data.wrong_answer_feedback
            }
            
            response = self.supabase.table("questions").insert(question_dict).execute()
            question_id = response.data[0]["id"]
            
            # Create choices for questions that have choices
            if question_data.choices:
                if question_data.question_type in [QuestionType.MULTIPLE_CHOICE, QuestionType.MATCHING]:
                    for choice_data in question_data.choices:
                        await self.create_choice(question_id, choice_data)
            
            # Fetch and return a complete question
            return await self.get_question_by_id(question_id)
        except Exception as e:
            logger.error(f"Error creating question: {e}")
            raise

    async def get_question_by_id(self, question_id: str) -> Optional[Question]:
        """Get a specific question with its choices"""
        try:
            response = self.supabase.table("questions").select(
                "*, question_choices(*)"
            ).eq("id", question_id).execute()
            
            if not response.data:
                return None
            
            q = response.data[0]
            
            choices = [
                QuestionChoice(
                    id=c["id"],
                    question_id=c["question_id"],
                    choice_text=c["choice_text"],
                    choice_order=c["choice_order"],
                    is_correct=c["is_correct"],
                    image_url=c.get("image_url"),
                    audio_url=c.get("audio_url"),
                    match_pair_id=c.get("match_pair_id"),  # ✅ Added for matching questions
                    created_at=c["created_at"]
                )
                for c in sorted(q.get("question_choices", []), key=lambda x: x["choice_order"])
            ]
            
            return Question(
                id=q["id"],
                lesson_id=q["lesson_id"],
                question_type=q["question_type"],
                question_text=q["question_text"],
                question_order=q["question_order"],
                answer_text=q.get("answer_text"),
                question_audio_url=q.get("question_audio_url"),
                answer_audio_url=q.get("answer_audio_url"),
                error_text=q.get("error_text"),  # ✅ Added
                explanation=q.get("explanation"),  # ✅ Added
                wrong_answer_feedback=q.get("wrong_answer_feedback"),  # ✅ Added
                choices=choices,
                created_at=q["created_at"],
                updated_at=q["updated_at"]
            )
        except Exception as e:
            logger.error(f"Error fetching question {question_id}: {e}")
            raise

    async def update_question(
        self, 
        question_id: str, 
        question_data: QuestionUpdate
    ) -> Question:
        """Update an existing question"""
        try:
            update_dict = question_data.model_dump(exclude_none=True)
            
            if "question_type" in update_dict:
                update_dict["question_type"] = update_dict["question_type"].value
            
            if update_dict:
                self.supabase.table("questions").update(update_dict).eq(
                    "id", question_id
                ).execute()
            
            return await self.get_question_by_id(question_id)
        except Exception as e:
            logger.error(f"Error updating question {question_id}: {e}")
            raise

    async def delete_question(self, question_id: str) -> bool:
        """Delete a question (cascade deletes choices)"""
        try:
            self.supabase.table("questions").delete().eq("id", question_id).execute()
            return True
        except Exception as e:
            logger.error(f"Error deleting question {question_id}: {e}")
            raise

   
    # CHOICE OPERATIONS
   
    
    async def create_choice(
        self,
        question_id: str,
        choice_data: QuestionChoiceCreate
    ) -> QuestionChoice:
        """Create a new choice for a question"""
        try:
            choice_dict = {
                "question_id": question_id,
                "choice_text": choice_data.choice_text,
                "choice_order": choice_data.choice_order,
                "is_correct": choice_data.is_correct,
                "image_url": choice_data.image_url,
                "audio_url": choice_data.audio_url,
                "match_pair_id": choice_data.match_pair_id  # ✅ Added for matching questions
            }

            response = self.supabase.table("question_choices").insert(choice_dict).execute()

            return QuestionChoice(**response.data[0])
        except Exception as e:
            logger.error(f"Error creating choice: {e}")
            raise

    async def delete_choice(self, choice_id: str) -> bool:
        """Delete a choice"""
        try:
            self.supabase.table("question_choices").delete().eq("id", choice_id).execute()
            return True
        except Exception as e:
            logger.error(f"Error deleting choice {choice_id}: {e}")
            raise

   
    # USER PROGRESS OPERATIONS
   
    
    async def get_user_lesson_progress(
        self, 
        user_id: str, 
        lesson_id: str
    ) -> Optional[UserLessonProgress]:
        """Get user's progress for a specific lesson"""
        try:
            response = self.supabase.table("user_lesson_progress").select("*").eq(
                "user_id", user_id
            ).eq("lesson_id", lesson_id).execute()
            
            if not response.data:
                return None
            
            return UserLessonProgress(**response.data[0])
        except Exception as e:
            logger.error(f"Error fetching progress: {e}")
            raise

    async def upsert_user_lesson_progress(
        self, 
        progress_data: UserLessonProgressCreate
    ) -> UserLessonProgress:
        """Create or update user's lesson progress"""
        try:
            progress_dict = progress_data.model_dump()
            progress_dict["last_accessed_at"] = datetime.utcnow().isoformat()
            
            if progress_data.is_completed:
                progress_dict["completed_at"] = datetime.utcnow().isoformat()
            
            response = self.supabase.table("user_lesson_progress").upsert(
                progress_dict
            ).execute()
            
            return UserLessonProgress(**response.data[0])
        except Exception as e:
            logger.error(f"Error upserting progress: {e}")
            raise

    async def submit_lesson_answers(
        self, 
        user_id: str, 
        lesson_id: str, 
        answers: List[UserQuestionAnswerCreate]
    ) -> Dict[str, Any]:
        """Submit user answers for a lesson and calculate score"""
        try:
            logger.info(f"[SUBMIT_SERVICE] ===== STARTING ANSWER SUBMISSION =====")
            logger.info(f"[SUBMIT_SERVICE] User ID: {user_id}")
            logger.info(f"[SUBMIT_SERVICE] Lesson ID: {lesson_id}")
            logger.info(f"[SUBMIT_SERVICE] Number of answers: {len(answers)}")
            
            # Insert all answers
            logger.info(f"[SUBMIT_SERVICE] Inserting answers...")
            for i, answer in enumerate(answers):
                logger.info(f"[SUBMIT_SERVICE] Answer {i+1}: {answer.model_dump()}")
                answer_dict = answer.model_dump()
                self.supabase.table("user_question_answers").upsert(answer_dict).execute()
            logger.info(f"[SUBMIT_SERVICE] All answers inserted successfully")
            
            # Calculate score
            logger.info(f"[SUBMIT_SERVICE] Calculating score...")
            correct_count = sum(1 for a in answers if a.is_correct)
            total_questions = len(answers)
            score = (correct_count / total_questions * 100) if total_questions > 0 else 0
            logger.info(f"[SUBMIT_SERVICE] Score calculation: {correct_count}/{total_questions} = {score}%")
            
            # Update lesson progress
            logger.info(f"[SUBMIT_SERVICE] Updating lesson progress...")
            progress_data = UserLessonProgressCreate(
                user_id=user_id,
                lesson_id=lesson_id,
                is_completed=True,
                score=score,
                time_spent_seconds=0  # Should be calculated from actual time
            )
            logger.info(f"[SUBMIT_SERVICE] Progress data: {progress_data.model_dump()}")
            
            await self.upsert_user_lesson_progress(progress_data)
            logger.info(f"[SUBMIT_SERVICE] Lesson progress updated successfully")
            
            result = {
                "score": score,
                "total_questions": total_questions,
                "correct_answers": correct_count,
                "is_passed": score >= 70,  # 70% passing a threshold
                "completed_at": datetime.utcnow().isoformat()
            }
            logger.info(f"[SUBMIT_SERVICE] ===== SUBMISSION COMPLETED =====")
            logger.info(f"[SUBMIT_SERVICE] Result: {result}")
            return result
        except Exception as e:
            logger.error(f"[SUBMIT_SERVICE] ===== SUBMISSION FAILED =====")
            logger.error(f"[SUBMIT_SERVICE] Error type: {type(e).__name__}")
            logger.error(f"[SUBMIT_SERVICE] Error message: {str(e)}")
            import traceback
            logger.error(f"[SUBMIT_SERVICE] Full traceback:\n{traceback.format_exc()}")
            raise
