package com.yourapp.llm

/**
 * Battle-tested prompts for Gemma 4 E2B to generate lecture study material.
 *
 * Rules I've learned:
 *   1. Gemma 4 is instruct-tuned — use imperative sentences.
 *   2. State the audience ("undergraduate student") — makes tone consistent.
 *   3. Ask for exact structure (bullets, JSON, numbered lists) — Gemma is great at following schemas.
 *   4. For long transcripts (2+ hrs), it helps to include a role framing "You are an expert note-taker."
 *   5. Never say "if you don't know, say so" — it makes Gemma too cautious for lectures.
 */
object GemmaPrompts {

    // ─── Summary ─────────────────────────────────────────────────────────
    fun summarize(transcript: String, maxWords: Int = 200): String = """
        You are an expert note-taker for an undergraduate student.
        Read this lecture transcript and write a $maxWords-word summary of what was taught.
        Focus on: main topic, key concepts, formulas / definitions mentioned, and the take-away message.
        Do NOT include filler like "the lecturer said" — just present the material as facts.
        Ignore administrative announcements (attendance, upcoming exams) unless critical.
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        Summary:
    """.trimIndent()

    // ─── Section headings + bullet notes ─────────────────────────────────
    fun sections(transcript: String): String = """
        You are creating study notes from this lecture transcript for an undergraduate student.
        Break the lecture into 4-8 sections. For each section:
        
        1. A short heading in Title Case (max 8 words).
        2. Below the heading, 3-6 bullet points of the actual content.
        3. If a section contains a formula, put the formula on its own line, wrapped in \$...\$ for LaTeX.
        4. If a section contains a definition, format it as "**Term:** explanation".
        
        Output format (use exactly this markdown structure):
        
        ## 1. Heading Here
        - First key point
        - Second key point
        - **Term:** definition
        
        ## 2. Next Heading
        - ...
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        Notes:
    """.trimIndent()

    // ─── Flashcards ──────────────────────────────────────────────────────
    fun flashcards(transcript: String, count: Int = 20): String = """
        Generate exactly $count flashcards from this lecture transcript to help a student study for an exam.
        
        Rules:
        - Each flashcard tests ONE concept.
        - Questions should be clear and specific (avoid "What did the lecturer say about X?")
        - Answers should be 1-3 sentences.
        - Include a mix of: definitions, formulas, applications, comparisons, and "why" questions.
        - Skip administrative content.
        
        Output as JSON array (nothing else — no markdown, no preamble):
        
        [
          {"q": "question here?", "a": "answer here"},
          {"q": "...", "a": "..."}
        ]
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        Flashcards:
    """.trimIndent()

    // ─── Key definitions & formulas ──────────────────────────────────────
    fun keyDefinitions(transcript: String): String = """
        Extract every important term, definition, and formula from this lecture transcript.
        
        Output as JSON with three arrays:
        {
          "terms": [{"term": "...", "definition": "..."}, ...],
          "formulas": [{"name": "...", "formula": "...", "explanation": "..."}, ...],
          "acronyms": [{"acronym": "...", "expansion": "..."}, ...]
        }
        
        Rules:
        - Only include items explicitly defined or explained in the lecture.
        - Use LaTeX \$...\$ for formulas.
        - No preamble, only the JSON.
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        JSON:
    """.trimIndent()

    // ─── Q&A over a lecture ──────────────────────────────────────────────
    fun answerQuestion(transcript: String, question: String): String = """
        You are a study assistant. A student is asking a question about a lecture they attended.
        Answer the question using ONLY information from the lecture transcript below.
        
        If the transcript does not contain the answer, say: "The lecture did not cover this specifically."
        Do not make up information.
        Keep the answer clear and concise (2-4 sentences unless a longer answer is needed).
        If relevant, cite the approximate timestamp from the transcript.
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        Student question: $question
        
        Answer:
    """.trimIndent()

    // ─── Quiz generation (bonus) ─────────────────────────────────────────
    fun quiz(transcript: String, questionCount: Int = 10): String = """
        Create a $questionCount-question multiple choice quiz based on this lecture transcript.
        
        Each question has 4 options (A, B, C, D) with exactly one correct answer.
        Distractors (wrong answers) should be plausible — not obviously silly.
        Include a mix of difficulty: 40% easy, 40% medium, 20% hard.
        
        Output as JSON:
        [
          {
            "question": "...",
            "options": {"A": "...", "B": "...", "C": "...", "D": "..."},
            "correct": "B",
            "difficulty": "medium",
            "explanation": "why this is correct"
          },
          ...
        ]
        
        --- TRANSCRIPT START ---
        $transcript
        --- TRANSCRIPT END ---
        
        Quiz:
    """.trimIndent()
}
