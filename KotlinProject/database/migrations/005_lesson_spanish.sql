create table public.lesson_spanish (
  id bigint not null,
  level text null,
  lesson bigint null,
  topic text null,
  type text null,
  component text null,
  content text[] null,
  romanized text[] null,
  english_translation text[] null,
  choices text[] null,
  constraint lesson_spanish_pkey primary key (id)
) TABLESPACE pg_default;