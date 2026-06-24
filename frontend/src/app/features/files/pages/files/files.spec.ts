import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {Files} from './files';
import {FilesService} from '../../../../core/services/files.service';
import {AuthService} from '../../../../core/services/auth.service';
import {provideI18n} from '../../../../../testing/provide-i18n';

describe('Files component', () => {
  let fixture: ComponentFixture<Files>;
  let component: Files;
  let filesService: {
    findPage: ReturnType<typeof vi.fn>;
    findAll: ReturnType<typeof vi.fn>;
    requestUpload: ReturnType<typeof vi.fn>;
    uploadToPresignedUrl: ReturnType<typeof vi.fn>;
    completeUpload: ReturnType<typeof vi.fn>;
    createDownloadUrl: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    const storedFile = {
      id: 1,
      originalFilename: 'guide.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1024,
      status: 'READY',
      ownerId: 1,
      ownerName: 'Admin Local',
      ownerEmail: 'admin@local.dev',
      uploadedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    filesService = {
      findPage: vi.fn().mockReturnValue(of({items: [storedFile], page: 0, size: 5, totalItems: 1, totalPages: 1})),
      findAll: vi.fn().mockReturnValue(of([storedFile])),
      requestUpload: vi.fn(),
      uploadToPresignedUrl: vi.fn(),
      completeUpload: vi.fn(),
      createDownloadUrl: vi.fn(),
      delete: vi.fn().mockReturnValue(of(void 0))
    };

    await TestBed.configureTestingModule({
      imports: [Files],
      providers: [
        provideI18n(),
        {provide: FilesService, useValue: filesService},
        {
          provide: AuthService,
          useValue: {
            getCurrentUserRole: vi.fn().mockReturnValue('ADMIN')
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Files);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('charge les fichiers au demarrage', () => {
    expect(filesService.findPage).toHaveBeenCalledWith({page: 0, size: 5, sort: 'updatedAt,desc'});
    expect(component.files().length).toBe(1);
    expect(component.files()[0].originalFilename).toBe('guide.pdf');
  });

  it('execute le flux d upload direct via URL presignee', () => {
    filesService.requestUpload.mockReturnValue(of({
      fileId: 2,
      uploadUrl: 'http://localhost:9000/upload',
      expiresAt: new Date().toISOString()
    }));
    filesService.uploadToPresignedUrl.mockReturnValue(of(void 0));
    filesService.completeUpload.mockReturnValue(of({
      id: 2,
      originalFilename: 'avatar.png',
      contentType: 'image/png',
      sizeBytes: 4,
      status: 'READY',
      ownerId: 1,
      ownerName: 'Admin Local',
      ownerEmail: 'admin@local.dev',
      uploadedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }));

    component.selectedLocalFile.set(new File(['test'], 'avatar.png', {type: 'image/png'}));

    component.uploadSelected();

    expect(filesService.requestUpload).toHaveBeenCalledWith({
      originalFilename: 'avatar.png',
      contentType: 'image/png',
      sizeBytes: 4
    });
    expect(filesService.uploadToPresignedUrl).toHaveBeenCalled();
    expect(filesService.completeUpload).toHaveBeenCalledWith(2);
    expect(component.files()[0].id).toBe(2);
  });
});
