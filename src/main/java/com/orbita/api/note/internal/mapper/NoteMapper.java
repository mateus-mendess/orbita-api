package com.orbita.api.note.internal.mapper;

import com.orbita.api.note.internal.dto.NoteRequest;
import com.orbita.api.note.internal.dto.NoteResponse;
import com.orbita.api.note.internal.model.Note;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NoteMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "userId", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Note toEntity(NoteRequest request);

	NoteResponse toResponse(Note note);

}
